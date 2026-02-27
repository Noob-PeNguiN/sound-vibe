package com.soundvibe.catalog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.soundvibe.catalog.model.dto.TrackPublishDTO;
import com.soundvibe.catalog.model.dto.TrackQueryDTO;
import com.soundvibe.catalog.model.dto.TrackUpdateDTO;
import com.soundvibe.catalog.model.vo.TrackVO;

/**
 * 作品服务接口
 * 定义作品发布、查询、状态切换、可见性管理等业务逻辑
 * <p>
 * 支持两种作品类型：
 * - SINGLE（单曲）：包含 1 个文件
 * - PACK（合集/采样包）：包含多个文件，可混合音频 + MIDI
 *
 * @author SoundVibe Team
 */
public interface TrackService {

    /**
     * 发布新作品（支持 SINGLE 和 PACK）
     *
     * @param dto    发布参数
     * @param userId 当前登录用户 ID（发布者）
     * @return 创建后的作品视图
     */
    TrackVO createTrack(TrackPublishDTO dto, Long userId);

    /**
     * 切换作品状态（私密 ↔ 公开）
     *
     * @param id     作品 ID
     * @param userId 当前登录用户 ID（用于鉴权）
     * @return 更新后的作品视图
     */
    TrackVO toggleStatus(Long id, Long userId);

    /**
     * 设置作品可见范围
     *
     * @param id         作品 ID
     * @param userId     当前登录用户 ID（用于鉴权）
     * @param visibility 可见范围: 0=仅自己, 1=指定用户, 2=公开
     * @return 更新后的作品视图
     */
    TrackVO setVisibility(Long id, Long userId, Integer visibility);

    /**
     * 获取作品详情（含 PACK 文件列表）
     *
     * @param id 作品 ID
     * @return 作品视图（PACK 类型会填充 files 列表）
     */
    TrackVO getDetail(Long id);

    /**
     * 更新作品信息（支持 SINGLE 和 PACK）
     * 仅更新传入的非 null 字段，支持部分更新
     * 对于 PACK 类型，如果传入 files 列表，将全量替换
     *
     * @param id     作品 ID
     * @param dto    更新参数（所有字段可选）
     * @param userId 当前登录用户 ID（用于鉴权）
     * @return 更新后的作品视图
     */
    TrackVO updateTrack(Long id, TrackUpdateDTO dto, Long userId);

    /**
     * 分页查询作品列表
     * 支持按关键词、标签、文件类型、作品类型、状态、可见性等条件筛选
     * 列表查询不返回 PACK 的 files 详情（仅返回 fileCount）
     *
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<TrackVO> listTracks(TrackQueryDTO query);

    /**
     * 删除作品（逻辑删除）
     * 同时逻辑删除关联的 track_files（PACK 类型）
     *
     * @param id     作品 ID
     * @param userId 当前登录用户 ID（用于鉴权）
     */
    void deleteTrack(Long id, Long userId);

    /**
     * 重建 ES 索引：将所有已上架作品重新发送到 RabbitMQ 以同步到 ES
     *
     * @return 重新索引的作品数量
     */
    int reindexAll();

    /**
     * 根据 assetId 重新同步关联的所有 track 到 ES
     * 典型场景：音频分析完成后，audio_vector 已写入 MySQL，需要更新 ES 索引
     *
     * @param assetId 资产 ID
     * @return 重新同步的作品数量
     */
    int resyncTracksByAssetId(Long assetId);
}
