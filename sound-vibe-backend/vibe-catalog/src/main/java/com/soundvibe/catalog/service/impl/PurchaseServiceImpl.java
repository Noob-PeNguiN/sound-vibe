package com.soundvibe.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soundvibe.catalog.domain.entity.Purchase;
import com.soundvibe.catalog.domain.entity.Track;
import com.soundvibe.catalog.domain.entity.TrackFile;
import com.soundvibe.catalog.enums.TrackType;
import com.soundvibe.catalog.enums.TrackVisibility;
import com.soundvibe.catalog.mapper.AssetMetadataMapper;
import com.soundvibe.catalog.mapper.PurchaseMapper;
import com.soundvibe.catalog.mapper.TrackFileMapper;
import com.soundvibe.catalog.mapper.TrackMapper;
import com.soundvibe.catalog.mapper.UserInfoMapper;
import com.soundvibe.catalog.model.vo.PurchaseVO;
import com.soundvibe.catalog.model.vo.TrackFileVO;
import com.soundvibe.catalog.model.vo.TrackVO;
import com.soundvibe.catalog.service.PurchaseService;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 购买服务实现类
 * <p>
 * 当前阶段：模拟购买（不涉及真实支付）
 * - 用户点击"购买"/"免费获取"后立即创建购买记录
 * - 不可购买自己的作品
 * - 同一作品不可重复购买
 *
 * @author SoundVibe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private final PurchaseMapper purchaseMapper;
    private final TrackMapper trackMapper;
    private final TrackFileMapper trackFileMapper;
    private final UserInfoMapper userInfoMapper;
    private final AssetMetadataMapper assetMetadataMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseVO purchaseTrack(Long trackId, Long userId) {
        // 1. 检查作品是否存在
        var track = trackMapper.selectById(trackId);
        if (track == null) {
            throw new BizException(ResultCode.NOT_FOUND, "作品不存在: id=" + trackId);
        }

        // 2. 不能购买自己的作品
        if (track.getProducerId().equals(userId)) {
            throw new BizException(ResultCode.BIZ_ERROR, "不能购买自己发布的作品");
        }

        // 3. 检查是否已购买
        if (checkAlreadyPurchased(trackId, userId)) {
            throw new BizException(ResultCode.BIZ_ERROR, "你已经购买过此作品");
        }

        // 4. 检查库存（stock 为 null 表示不限库存）
        if (track.getStock() != null && track.getStock() <= 0) {
            throw new BizException(ResultCode.OUT_OF_STOCK);
        }

        // 5. 创建购买记录（模拟支付：直接成功）
        var purchase = new Purchase();
        purchase.setUserId(userId);
        purchase.setTrackId(trackId);
        purchase.setPricePaid(track.getPrice() != null ? track.getPrice() : BigDecimal.ZERO);
        purchaseMapper.insert(purchase);

        // 6. 更新已售数量 & 扣减库存
        track.setSoldCount((track.getSoldCount() != null ? track.getSoldCount() : 0) + 1);
        if (track.getStock() != null) {
            track.setStock(track.getStock() - 1);
        }
        trackMapper.updateById(track);

        log.info("购买成功: purchaseId={}, trackId={}, userId={}, pricePaid={}",
                purchase.getId(), trackId, userId, purchase.getPricePaid());

        // 5. 返回购买记录 VO（含关联的作品信息）
        return toPurchaseVO(purchase, track);
    }

    @Override
    public IPage<PurchaseVO> listMyPurchases(Long userId, long current, long size) {
        // 1. 分页查询购买记录
        var wrapper = new LambdaQueryWrapper<Purchase>();
        wrapper.eq(Purchase::getUserId, userId);
        wrapper.orderByDesc(Purchase::getCreateTime);

        var page = new Page<Purchase>(current, size);
        var result = purchaseMapper.selectPage(page, wrapper);

        if (result.getRecords().isEmpty()) {
            return result.convert(p -> null); // 空页
        }

        // 2. 批量加载关联的作品
        var trackIds = result.getRecords().stream()
                .map(Purchase::getTrackId)
                .distinct()
                .collect(Collectors.toList());

        var tracks = trackMapper.selectBatchIds(trackIds);
        var trackMap = tracks.stream()
                .collect(Collectors.toMap(Track::getId, t -> t, (a, b) -> a));

        // 3. 批量查询发布者用户名
        var producerIds = tracks.stream()
                .map(Track::getProducerId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> usernameMap = Map.of();
        if (!producerIds.isEmpty()) {
            usernameMap = userInfoMapper.selectUsernamesByIds(producerIds).stream()
                    .collect(Collectors.toMap(
                            m -> ((Number) m.get("id")).longValue(),
                            m -> (String) m.get("username"),
                            (a, b) -> a
                    ));
        }

        // 4. 转换为 VO
        final var nameMap = usernameMap;
        return result.convert(purchase -> {
            var track = trackMap.get(purchase.getTrackId());
            if (track == null) {
                // 作品已被删除，仍展示购买记录但 track 为 null
                return new PurchaseVO(
                        purchase.getId(),
                        purchase.getUserId(),
                        purchase.getTrackId(),
                        purchase.getPricePaid(),
                        null,
                        purchase.getCreateTime()
                );
            }
            return toPurchaseVOWithName(purchase, track, nameMap.get(track.getProducerId()));
        });
    }

    @Override
    public boolean hasPurchased(Long trackId, Long userId) {
        return checkAlreadyPurchased(trackId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPurchase(Long trackId, Long userId, BigDecimal pricePaid) {
        if (checkAlreadyPurchased(trackId, userId)) {
            log.info("confirmPurchase 幂等跳过: trackId={}, userId={} 已存在购买记录", trackId, userId);
            return;
        }

        var purchase = new Purchase();
        purchase.setUserId(userId);
        purchase.setTrackId(trackId);
        purchase.setPricePaid(pricePaid != null ? pricePaid : BigDecimal.ZERO);
        purchaseMapper.insert(purchase);

        var track = trackMapper.selectById(trackId);
        if (track != null) {
            track.setSoldCount((track.getSoldCount() != null ? track.getSoldCount() : 0) + 1);
            if (track.getStock() != null && track.getStock() > 0) {
                track.setStock(track.getStock() - 1);
            }
            trackMapper.updateById(track);
        }

        log.info("confirmPurchase 成功: purchaseId={}, trackId={}, userId={}, pricePaid={}",
                purchase.getId(), trackId, userId, pricePaid);
    }

    // ======================== Private ========================

    private boolean checkAlreadyPurchased(Long trackId, Long userId) {
        var wrapper = new LambdaQueryWrapper<Purchase>();
        wrapper.eq(Purchase::getUserId, userId);
        wrapper.eq(Purchase::getTrackId, trackId);
        return purchaseMapper.selectCount(wrapper) > 0;
    }

    /**
     * 构建 PurchaseVO（含关联的 TrackVO）
     */
    private PurchaseVO toPurchaseVO(Purchase purchase, Track track) {
        var producerName = userInfoMapper.selectUsernameById(track.getProducerId());
        return toPurchaseVOWithName(purchase, track, producerName);
    }

    private PurchaseVO toPurchaseVOWithName(Purchase purchase, Track track, String producerName) {
        // 加载文件列表（SINGLE 和 PACK 都通过 track_files 管理）
        var wrapper = new LambdaQueryWrapper<TrackFile>();
        wrapper.eq(TrackFile::getTrackId, track.getId());
        wrapper.orderByAsc(TrackFile::getSortOrder);
        List<TrackFile> files = trackFileMapper.selectList(wrapper);

        // 加载 asset 分析元数据
        var assetIds = files.stream()
                .map(TrackFile::getAssetId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Map<String, Object>> assetMetaMap = Map.of();
        if (!assetIds.isEmpty()) {
            try {
                assetMetaMap = assetMetadataMapper.selectMetadataByIds(assetIds).stream()
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("id")).longValue(),
                                m -> m,
                                (a, b) -> a
                        ));
            } catch (Exception e) {
                log.warn("购买记录加载 asset 元数据失败: {}", e.getMessage());
            }
        }

        final var metaMap = assetMetaMap;
        List<TrackFileVO> fileVOs = files.stream()
                .map(tf -> {
                    var meta = metaMap.getOrDefault(tf.getAssetId(), Map.of());
                    return new TrackFileVO(
                            tf.getId(),
                            tf.getAssetId(),
                            tf.getFileType(),
                            tf.getOriginalName(),
                            tf.getSortOrder(),
                            tf.getAllowPreview() != null ? tf.getAllowPreview() : true,
                            toInteger(meta.get("bpm")),
                            (String) meta.get("musical_key"),
                            toInteger(meta.get("duration")),
                            (String) meta.get("auto_tags")
                    );
                })
                .collect(Collectors.toList());

        String aggregatedAutoTags = fileVOs.stream()
                .map(TrackFileVO::autoTags)
                .filter(Objects::nonNull)
                .flatMap(t -> Arrays.stream(t.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));

        var trackVO = new TrackVO(
                track.getId(),
                track.getTitle(),
                track.getDescription(),
                track.getTrackType() != null ? track.getTrackType().getValue() : TrackType.SINGLE.getValue(),
                track.getProducerId(),
                producerName,
                track.getCoverId(),
                track.getFileId(),
                track.getFileType(),
                track.getFileCount() != null ? track.getFileCount() : 1,
                fileVOs.isEmpty() ? null : fileVOs,
                track.getPrice(),
                track.getStatus().getCode(),
                track.getVisibility() != null ? track.getVisibility().getCode() : TrackVisibility.PUBLIC.getCode(),
                track.getTags(),
                aggregatedAutoTags.isEmpty() ? null : aggregatedAutoTags,
                track.getGenre(),
                track.getAllowPreview() != null ? track.getAllowPreview() : true,
                track.getPreviewDuration() != null ? track.getPreviewDuration() : 30,
                track.getSoldCount() != null ? track.getSoldCount() : 0,
                track.getStock(),
                track.getCreateTime(),
                track.getUpdateTime()
        );

        return new PurchaseVO(
                purchase.getId(),
                purchase.getUserId(),
                purchase.getTrackId(),
                purchase.getPricePaid(),
                trackVO,
                purchase.getCreateTime()
        );
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }
}
