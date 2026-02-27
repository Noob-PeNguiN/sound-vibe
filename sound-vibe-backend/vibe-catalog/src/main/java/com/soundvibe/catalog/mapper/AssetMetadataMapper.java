package com.soundvibe.catalog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 资产元数据只读 Mapper
 * 跨表读取 assets 表的 BPM / 调式 / 时长等分析数据
 * <p>
 * 设计说明：
 * - 仅读取 assets 表中由分析服务填充的元数据字段
 * - 用于 Catalog 模块构建 TrackVO 时关联资产分析信息
 * - 因共享同一个数据库（sound_vibe_db），可直接 SQL 查询，无需跨服务 Feign 调用
 *
 * @author SoundVibe Team
 */
@Mapper
public interface AssetMetadataMapper {

    /**
     * 批量查询指定资产 ID 的分析元数据
     * 返回 Map 包含: id, bpm, musical_key, duration
     *
     * @param ids 资产 ID 列表
     * @return 每行一个 Map，key 为字段名
     */
    @Select("<script>" +
            "SELECT id, bpm, musical_key, duration, auto_tags " +
            "FROM assets " +
            "WHERE deleted = 0 AND id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<Map<String, Object>> selectMetadataByIds(@Param("ids") List<Long> ids);

    /**
     * 批量查询指定资产 ID 的 CLAP 特征向量
     * audio_vector 存储在 assets 表中（JSON 数组格式）
     *
     * @param ids 资产 ID 列表
     * @return 每行一个 Map，key 为 id 和 audio_vector
     */
    @Select("<script>" +
            "SELECT id AS asset_id, audio_vector AS feature_vector " +
            "FROM assets " +
            "WHERE deleted = 0 AND audio_vector IS NOT NULL AND id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<Map<String, Object>> selectFeatureVectorByIds(@Param("ids") List<Long> ids);
}
