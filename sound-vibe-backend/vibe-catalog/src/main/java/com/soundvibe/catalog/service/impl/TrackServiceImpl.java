package com.soundvibe.catalog.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soundvibe.catalog.domain.entity.Track;
import com.soundvibe.catalog.domain.entity.TrackFile;
import com.soundvibe.catalog.enums.TrackStatus;
import com.soundvibe.catalog.enums.TrackType;
import com.soundvibe.catalog.enums.TrackVisibility;
import com.soundvibe.catalog.mapper.AssetMetadataMapper;
import com.soundvibe.catalog.mapper.TrackFileMapper;
import com.soundvibe.catalog.mapper.TrackMapper;
import com.soundvibe.catalog.mapper.UserInfoMapper;
import com.soundvibe.catalog.model.dto.TrackFileDTO;
import com.soundvibe.catalog.model.dto.TrackPublishDTO;
import com.soundvibe.catalog.model.dto.TrackQueryDTO;
import com.soundvibe.catalog.model.dto.TrackUpdateDTO;
import com.soundvibe.catalog.model.vo.TrackFileVO;
import com.soundvibe.catalog.model.vo.TrackVO;
import com.soundvibe.catalog.service.TrackService;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 作品服务实现类
 * 处理作品发布、查询、状态切换、可见性管理等核心业务逻辑
 * 支持 SINGLE（单曲）和 PACK（合集/采样包）两种模式
 * <p>
 * v2 重构说明：
 * - SINGLE 和 PACK 都通过 track_files 中间表管理文件关联
 * - BPM / 调式 / 时长等分析数据从 assets 表获取（通过 AssetMetadataMapper）
 * - 不再使用 Feign 调用 vibe-asset 获取元数据
 *
 * @author SoundVibe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackServiceImpl implements TrackService {

    private final TrackMapper trackMapper;
    private final TrackFileMapper trackFileMapper;
    private final UserInfoMapper userInfoMapper;
    private final AssetMetadataMapper assetMetadataMapper;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 作品同步队列名称（与 vibe-search 的 RabbitMQConfig.TRACK_SYNC_QUEUE 一致）
     */
    private static final String TRACK_SYNC_QUEUE = "soundvibe.track.sync.queue";

    /**
     * 允许的文件类型集合
     */
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("AUDIO", "MIDI");

    /**
     * 默认文件类型
     */
    private static final String DEFAULT_FILE_TYPE = "AUDIO";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackVO createTrack(TrackPublishDTO dto, Long userId) {
        // 1. 判断作品类型
        var trackType = resolveTrackType(dto.trackType());
        boolean isPack = trackType == TrackType.PACK;

        // 2. 校验参数
        if (isPack) {
            if (dto.files() == null || dto.files().isEmpty()) {
                throw new BizException(ResultCode.PARAM_ERROR, "合集类型必须包含至少 1 个文件");
            }
        } else {
            if (dto.fileId() == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "单曲类型必须指定文件资产 ID");
            }
        }

        // 3. 构建 Track 实体
        var track = new Track();
        track.setTitle(dto.title().trim());
        track.setDescription(dto.description());
        track.setTrackType(trackType);
        track.setProducerId(userId);
        track.setCoverId(dto.coverId());
        track.setPrice(dto.price());
        track.setTags(dto.tags());
        track.setGenre(dto.genre());
        track.setAllowPreview(dto.allowPreview() != null ? dto.allowPreview() : true);
        track.setPreviewDuration(dto.previewDuration() != null ? dto.previewDuration() : 30);
        track.setSoldCount(0);
        track.setStock(dto.stock()); // null = 不限库存
        track.setStatus(TrackStatus.PUBLISHED);

        if (isPack) {
            // PACK: file_id / file_type 为 null，数量为文件列表长度
            track.setFileId(null);
            track.setFileType(null);
            track.setFileCount(dto.files().size());
        } else {
            // SINGLE: 直接存 file_id / file_type（便捷冗余字段）
            track.setFileId(dto.fileId());
            track.setFileType(resolveFileType(dto.fileType()));
            track.setFileCount(1);
        }

        // 可见范围：默认公开
        if (dto.visibility() != null) {
            track.setVisibility(resolveVisibility(dto.visibility()));
        } else {
            track.setVisibility(TrackVisibility.PUBLIC);
        }

        // 4. 持久化 Track
        trackMapper.insert(track);

        // 5. 持久化文件列表（SINGLE 和 PACK 统一通过 track_files 管理）
        List<TrackFile> trackFiles;
        if (isPack) {
            trackFiles = insertTrackFiles(track.getId(), dto.files());
        } else {
            // SINGLE: 也创建一条 track_files 记录
            trackFiles = insertSingleTrackFile(track.getId(), dto.fileId(), dto.fileType());
        }

        log.info("作品发布成功: id={}, type={}, title={}, producerId={}, fileCount={}, visibility={}",
                track.getId(), trackType, track.getTitle(), userId,
                track.getFileCount(), track.getVisibility());

        // 6. 异步发送消息到搜索服务同步 ES 索引
        var fileVOs = buildFileVOs(trackFiles);
        sendTrackSyncMessage(track, fileVOs);

        // 7. 返回 VO
        var username = userInfoMapper.selectUsernameById(userId);
        return buildTrackVO(track, username, fileVOs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackVO toggleStatus(Long id, Long userId) {
        var track = findByIdOrThrow(id);
        checkOwnership(track, userId);

        // 切换状态：私密 <-> 公开，同时联动可见范围
        if (track.getStatus() == TrackStatus.DRAFT) {
            track.setStatus(TrackStatus.PUBLISHED);
            track.setVisibility(TrackVisibility.PUBLIC);
        } else {
            track.setStatus(TrackStatus.DRAFT);
            track.setVisibility(TrackVisibility.PRIVATE);
        }
        trackMapper.updateById(track);

        log.info("作品状态切换: id={}, newStatus={}, newVisibility={}, userId={}",
                id, track.getStatus(), track.getVisibility(), userId);
        return toVO(track);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackVO setVisibility(Long id, Long userId, Integer visibility) {
        var track = findByIdOrThrow(id);
        checkOwnership(track, userId);

        var newVisibility = resolveVisibility(visibility);
        track.setVisibility(newVisibility);
        trackMapper.updateById(track);

        log.info("作品可见范围变更: id={}, visibility={}, userId={}", id, newVisibility, userId);
        return toVO(track);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackVO updateTrack(Long id, TrackUpdateDTO dto, Long userId) {
        var track = findByIdOrThrow(id);
        checkOwnership(track, userId);

        boolean isPack = track.getTrackType() == TrackType.PACK;

        // 仅更新非 null 字段（部分更新）
        if (dto.title() != null) {
            track.setTitle(dto.title().trim());
        }
        if (dto.description() != null) {
            track.setDescription(dto.description());
        }

        // SINGLE 类型：更新 fileId / fileType
        if (!isPack) {
            if (dto.fileId() != null) {
                track.setFileId(dto.fileId());
                // 同步更新 track_files 记录
                deleteTrackFiles(id);
                insertSingleTrackFile(id, dto.fileId(), dto.fileType());
            }
            if (dto.fileType() != null) {
                track.setFileType(resolveFileType(dto.fileType()));
            }
        }

        // PACK 类型：全量替换文件列表
        if (isPack && dto.files() != null) {
            // 先逻辑删除旧文件
            deleteTrackFiles(id);
            // 插入新文件
            var newFiles = insertTrackFiles(id, dto.files());
            track.setFileCount(newFiles.size());
        }

        if (dto.coverId() != null) {
            track.setCoverId(dto.coverId());
        }
        if (dto.tags() != null) {
            track.setTags(dto.tags());
        }
        if (dto.genre() != null) {
            track.setGenre(dto.genre());
        }
        if (dto.price() != null) {
            track.setPrice(dto.price());
        }
        if (dto.visibility() != null) {
            track.setVisibility(resolveVisibility(dto.visibility()));
        }
        if (dto.allowPreview() != null) {
            track.setAllowPreview(dto.allowPreview());
        }
        if (dto.previewDuration() != null) {
            track.setPreviewDuration(dto.previewDuration());
        }
        if (dto.stock() != null) {
            track.setStock(dto.stock());
        }

        trackMapper.updateById(track);
        log.info("作品信息更新: id={}, type={}, userId={}", id, track.getTrackType(), userId);

        // 更新后同步 ES 索引
        var fileVOs = buildFileVOs(loadTrackFiles(id));
        sendTrackSyncMessage(track, fileVOs);

        var username = userInfoMapper.selectUsernameById(track.getProducerId());
        return buildTrackVO(track, username, fileVOs);
    }

    @Override
    public TrackVO getDetail(Long id) {
        var track = findByIdOrThrow(id);
        // 详情页：始终加载文件列表（SINGLE 和 PACK 都有）
        var files = loadTrackFiles(id);
        var fileVOs = buildFileVOs(files);
        var username = userInfoMapper.selectUsernameById(track.getProducerId());
        return buildTrackVO(track, username, fileVOs);
    }

    @Override
    public IPage<TrackVO> listTracks(TrackQueryDTO query) {
        var wrapper = new LambdaQueryWrapper<Track>();

        // 关键词模糊搜索（标题 + 标签 + 描述）
        if (StrUtil.isNotBlank(query.keyword())) {
            wrapper.and(w -> w
                    .like(Track::getTitle, query.keyword())
                    .or()
                    .like(Track::getTags, query.keyword())
                    .or()
                    .like(Track::getDescription, query.keyword())
            );
        }

        // 标签精确匹配
        wrapper.like(StrUtil.isNotBlank(query.tag()), Track::getTags, query.tag());

        // 文件类型过滤（仅对 SINGLE 有效）
        wrapper.eq(StrUtil.isNotBlank(query.fileType()), Track::getFileType, query.fileType());

        // 作品类型过滤（SINGLE / PACK）
        wrapper.eq(StrUtil.isNotBlank(query.trackType()), Track::getTrackType, query.trackType());

        // 发布者过滤
        boolean isMyTracks = query.producerId() != null;
        if (isMyTracks) {
            wrapper.eq(Track::getProducerId, query.producerId());
        }

        // 状态过滤
        if (query.status() != null) {
            wrapper.eq(Track::getStatus, query.status());
        } else if (!isMyTracks) {
            wrapper.eq(Track::getStatus, TrackStatus.PUBLISHED);
        }

        // 可见范围过滤
        if (query.visibility() != null) {
            wrapper.eq(Track::getVisibility, query.visibility());
        } else if (!isMyTracks) {
            wrapper.eq(Track::getVisibility, TrackVisibility.PUBLIC);
        }

        // 按创建时间降序
        wrapper.orderByDesc(Track::getCreateTime);

        // 分页查询
        var page = new Page<Track>(query.current(), query.size());
        var result = trackMapper.selectPage(page, wrapper);

        // 批量查询发布者用户名
        var producerIds = result.getRecords().stream()
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

        // 批量加载所有 track 的文件列表（含 asset 元数据）
        var trackIds = result.getRecords().stream().map(Track::getId).collect(Collectors.toList());
        Map<Long, List<TrackFileVO>> trackFileMap = batchLoadFileVOs(trackIds);

        // 转换为 VO
        final var nameMap = usernameMap;
        return result.convert(track -> buildTrackVO(
                track,
                nameMap.get(track.getProducerId()),
                trackFileMap.getOrDefault(track.getId(), List.of())
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTrack(Long id, Long userId) {
        var track = findByIdOrThrow(id);
        checkOwnership(track, userId);

        // 逻辑删除关联的文件（SINGLE 和 PACK 都有 track_files 记录）
        deleteTrackFiles(id);

        // 逻辑删除作品
        trackMapper.deleteById(id);
        log.info("作品已删除: id={}, type={}, userId={}", id, track.getTrackType(), userId);
    }

    // ======================== Track Files CRUD ========================

    /**
     * 批量插入 PACK 的文件列表
     */
    private List<TrackFile> insertTrackFiles(Long trackId, List<TrackFileDTO> files) {
        var result = new ArrayList<TrackFile>();
        for (int i = 0; i < files.size(); i++) {
            var dto = files.get(i);
            var tf = new TrackFile();
            tf.setTrackId(trackId);
            tf.setAssetId(dto.assetId());
            tf.setFileType(resolveFileType(dto.fileType()));
            tf.setOriginalName(dto.originalName());
            tf.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : i);
            tf.setAllowPreview(dto.allowPreview() != null ? dto.allowPreview() : true);
            trackFileMapper.insert(tf);
            result.add(tf);
        }
        return result;
    }

    /**
     * 为 SINGLE 类型创建一条 track_files 记录
     */
    private List<TrackFile> insertSingleTrackFile(Long trackId, Long assetId, String fileType) {
        var tf = new TrackFile();
        tf.setTrackId(trackId);
        tf.setAssetId(assetId);
        tf.setFileType(resolveFileType(fileType));
        tf.setSortOrder(0);
        tf.setAllowPreview(true);
        trackFileMapper.insert(tf);
        return List.of(tf);
    }

    /**
     * 逻辑删除指定 Track 的所有文件
     */
    private void deleteTrackFiles(Long trackId) {
        var wrapper = new LambdaQueryWrapper<TrackFile>();
        wrapper.eq(TrackFile::getTrackId, trackId);
        var existing = trackFileMapper.selectList(wrapper);
        for (var tf : existing) {
            trackFileMapper.deleteById(tf.getId());
        }
    }

    /**
     * 加载指定 Track 的文件列表（按 sort_order 升序）
     */
    private List<TrackFile> loadTrackFiles(Long trackId) {
        var wrapper = new LambdaQueryWrapper<TrackFile>();
        wrapper.eq(TrackFile::getTrackId, trackId);
        wrapper.orderByAsc(TrackFile::getSortOrder);
        return trackFileMapper.selectList(wrapper);
    }

    // ======================== Asset 元数据关联 ========================

    /**
     * 将 TrackFile 列表转换为 TrackFileVO 列表（附带 asset 分析元数据）
     */
    private List<TrackFileVO> buildFileVOs(List<TrackFile> trackFiles) {
        if (trackFiles == null || trackFiles.isEmpty()) {
            return List.of();
        }

        // 收集所有 assetId
        var assetIds = trackFiles.stream()
                .map(TrackFile::getAssetId)
                .distinct()
                .collect(Collectors.toList());

        // 批量查询 asset 元数据
        Map<Long, Map<String, Object>> assetMetaMap = loadAssetMetadata(assetIds);

        // 组装 VO
        return trackFiles.stream().map(tf -> {
            var meta = assetMetaMap.getOrDefault(tf.getAssetId(), Map.of());
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
        }).collect(Collectors.toList());
    }

    /**
     * 批量加载多个 Track 的文件 VO（列表页批量优化）
     */
    private Map<Long, List<TrackFileVO>> batchLoadFileVOs(List<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return Map.of();
        }

        // 批量查询所有 track_files
        var wrapper = new LambdaQueryWrapper<TrackFile>();
        wrapper.in(TrackFile::getTrackId, trackIds);
        wrapper.orderByAsc(TrackFile::getSortOrder);
        var allFiles = trackFileMapper.selectList(wrapper);

        if (allFiles.isEmpty()) {
            return Map.of();
        }

        // 收集所有 assetId 并批量查询元数据
        var assetIds = allFiles.stream()
                .map(TrackFile::getAssetId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Map<String, Object>> assetMetaMap = loadAssetMetadata(assetIds);

        // 按 trackId 分组转换为 VO
        return allFiles.stream().collect(Collectors.groupingBy(
                TrackFile::getTrackId,
                Collectors.mapping(tf -> {
                    var meta = assetMetaMap.getOrDefault(tf.getAssetId(), Map.of());
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
                }, Collectors.toList())
        ));
    }

    /**
     * 批量加载 asset 分析元数据
     */
    private Map<Long, Map<String, Object>> loadAssetMetadata(List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Map.of();
        }
        try {
            var metaList = assetMetadataMapper.selectMetadataByIds(assetIds);
            return metaList.stream().collect(Collectors.toMap(
                    m -> ((Number) m.get("id")).longValue(),
                    m -> m,
                    (a, b) -> a
            ));
        } catch (Exception e) {
            log.warn("加载 asset 元数据失败（不影响主流程）: assetIds={}, error={}", assetIds, e.getMessage());
            return Map.of();
        }
    }

    // ======================== Private Helpers ========================

    private Track findByIdOrThrow(Long id) {
        var track = trackMapper.selectById(id);
        if (track == null) {
            throw new BizException(ResultCode.NOT_FOUND, "作品不存在: id=" + id);
        }
        return track;
    }

    private void checkOwnership(Track track, Long userId) {
        if (!track.getProducerId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作此作品");
        }
    }

    private TrackVisibility resolveVisibility(Integer code) {
        if (code == null) {
            return TrackVisibility.PUBLIC;
        }
        for (var v : TrackVisibility.values()) {
            if (v.getCode() == code) {
                return v;
            }
        }
        throw new BizException(ResultCode.PARAM_ERROR, "不支持的可见范围: " + code);
    }

    private String resolveFileType(String fileType) {
        if (StrUtil.isBlank(fileType)) {
            return DEFAULT_FILE_TYPE;
        }
        var type = fileType.toUpperCase();
        if (!ALLOWED_FILE_TYPES.contains(type)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的文件类型: " + fileType + "，允许: " + ALLOWED_FILE_TYPES);
        }
        return type;
    }

    /**
     * 解析作品类型（默认 SINGLE）
     */
    private TrackType resolveTrackType(String trackType) {
        if (StrUtil.isBlank(trackType)) {
            return TrackType.SINGLE;
        }
        try {
            return TrackType.valueOf(trackType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的作品类型: " + trackType + "，允许: SINGLE, PACK");
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    // ======================== VO 转换 ========================

    /**
     * 快捷 VO 转换（自动加载 username + files）
     */
    private TrackVO toVO(Track track) {
        var username = userInfoMapper.selectUsernameById(track.getProducerId());
        var files = loadTrackFiles(track.getId());
        var fileVOs = buildFileVOs(files);
        return buildTrackVO(track, username, fileVOs);
    }

    /**
     * 核心 VO 构建方法
     */
    private TrackVO buildTrackVO(Track track, String producerName, List<TrackFileVO> fileVOs) {
        String aggregatedAutoTags = fileVOs.stream()
                .map(TrackFileVO::autoTags)
                .filter(Objects::nonNull)
                .flatMap(t -> java.util.Arrays.stream(t.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));

        return new TrackVO(
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
    }

    // ======================== 消息同步 ========================

    /**
     * 发送作品同步消息到 RabbitMQ
     * 消息包含 track 元数据 + 从 assets 聚合的 BPM/Key/Duration 数组
     * 非阻塞：发送失败仅记录日志，不影响主流程
     */
    private void sendTrackSyncMessage(Track track, List<TrackFileVO> fileVOs) {
        try {
            var producerName = userInfoMapper.selectUsernameById(track.getProducerId());

            // 从 fileVOs 聚合分析数据（去重、过滤 null）
            List<Integer> bpmValues = fileVOs.stream()
                    .map(TrackFileVO::bpm)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            List<String> musicalKeys = fileVOs.stream()
                    .map(TrackFileVO::musicalKey)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            List<Integer> durations = fileVOs.stream()
                    .map(TrackFileVO::duration)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<String> autoTagsList = fileVOs.stream()
                    .map(TrackFileVO::autoTags)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // 构建消息 Map（vibe-search 的 TrackSyncListener 以 Map 接收）
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("id", track.getId());
            message.put("title", track.getTitle());
            message.put("trackType", track.getTrackType() != null ? track.getTrackType().getValue() : "SINGLE");
            message.put("producerId", track.getProducerId());
            message.put("producerName", producerName);
            message.put("coverId", track.getCoverId());
            message.put("fileId", track.getFileId());
            message.put("tags", track.getTags());
            message.put("price", track.getPrice());
            message.put("status", track.getStatus().getCode());
            // 聚合的分析数据数组
            message.put("bpmValues", bpmValues);
            message.put("musicalKeys", musicalKeys);
            message.put("durations", durations);
            message.put("autoTags", autoTagsList);

            // CLAP 音频特征向量：取第一个音频文件的 feature_vector 用于语义搜索
            List<Long> assetIds = fileVOs.stream()
                    .map(TrackFileVO::assetId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!assetIds.isEmpty()) {
                try {
                    var vectorRows = assetMetadataMapper.selectFeatureVectorByIds(assetIds);
                    for (var row : vectorRows) {
                        Object vectorData = row.get("feature_vector");
                        if (vectorData != null) {
                            String vectorJson = vectorData.toString();
                            List<Float> audioVector = com.alibaba.fastjson2.JSON.parseArray(vectorJson, Float.class);
                            if (audioVector != null && !audioVector.isEmpty()) {
                                message.put("audioVector", audioVector);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("加载 feature_vector 失败（不影响同步）: assetIds={}, error={}", assetIds, e.getMessage());
                }
            }

            rabbitTemplate.convertAndSend(TRACK_SYNC_QUEUE, message);
            log.info("作品同步消息已发送: trackId={}, title={}, bpmValues={}, musicalKeys={}, hasVector={}",
                    track.getId(), track.getTitle(), bpmValues, musicalKeys, message.containsKey("audioVector"));
        } catch (Exception e) {
            // 消息发送失败不应影响主业务流程
            log.error("作品同步消息发送失败: trackId={}, error={}", track.getId(), e.getMessage(), e);
        }
    }

    // ======================== 全量重建索引 ========================

    @Override
    public int reindexAll() {
        var wrapper = new LambdaQueryWrapper<Track>()
                .eq(Track::getStatus, TrackStatus.PUBLISHED);
        List<Track> publishedTracks = trackMapper.selectList(wrapper);

        int count = 0;
        for (Track track : publishedTracks) {
            try {
                var files = loadTrackFiles(track.getId());
                var fileVOs = buildFileVOs(files);
                sendTrackSyncMessage(track, fileVOs);
                count++;
            } catch (Exception e) {
                log.error("重建索引失败: trackId={}, error={}", track.getId(), e.getMessage(), e);
            }
        }
        log.info("ES 全量重建索引完成: 共 {} 条作品", count);
        return count;
    }

    @Override
    public int resyncTracksByAssetId(Long assetId) {
        // 查询引用此 asset 的所有 track_files 记录
        var tfWrapper = new LambdaQueryWrapper<TrackFile>()
                .eq(TrackFile::getAssetId, assetId);
        List<TrackFile> trackFiles = trackFileMapper.selectList(tfWrapper);

        if (trackFiles.isEmpty()) {
            log.debug("未找到引用 assetId={} 的作品，跳过重新同步", assetId);
            return 0;
        }

        // 获取不重复的 trackId 列表
        List<Long> trackIds = trackFiles.stream()
                .map(TrackFile::getTrackId)
                .distinct()
                .collect(Collectors.toList());

        int count = 0;
        for (Long trackId : trackIds) {
            try {
                Track track = trackMapper.selectById(trackId);
                if (track == null || track.getStatus() != TrackStatus.PUBLISHED) {
                    continue;
                }
                var files = loadTrackFiles(trackId);
                var fileVOs = buildFileVOs(files);
                sendTrackSyncMessage(track, fileVOs);
                count++;
                log.info("分析完成后重新同步 ES: trackId={}, assetId={}", trackId, assetId);
            } catch (Exception e) {
                log.error("分析完成后重新同步失败: trackId={}, assetId={}, error={}",
                        trackId, assetId, e.getMessage(), e);
            }
        }
        log.info("assetId={} 分析完成，已重新同步 {} 条作品到 ES", assetId, count);
        return count;
    }
}
