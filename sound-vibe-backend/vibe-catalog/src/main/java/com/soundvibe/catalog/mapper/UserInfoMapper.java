package com.soundvibe.catalog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 用户信息只读 Mapper（跨模块查询）
 * <p>
 * 架构说明：
 * 当前所有微服务共享同一个 MySQL 实例（sound_vibe_db），
 * 因此 vibe-catalog 可以直接读取 users 表获取 username。
 * <p>
 * 未来若拆分数据库，此 Mapper 应替换为 OpenFeign 远程调用 vibe-auth 服务。
 * <p>
 * 设计约束：
 * - 只读操作，不写入 users 表
 * - 只查询 id 和 username，不暴露密码等敏感字段
 *
 * @author SoundVibe Team
 */
@Mapper
public interface UserInfoMapper {

    /**
     * 根据用户 ID 查询用户名
     *
     * @param userId 用户 ID
     * @return 用户名，不存在则返回 null
     */
    @Select("SELECT username FROM users WHERE id = #{userId}")
    String selectUsernameById(@Param("userId") Long userId);

    /**
     * 批量查询用户名（用于列表页优化，避免 N+1 查询）
     * 返回 List<Map>，每个 Map 包含 id 和 username
     *
     * @param userIds 用户 ID 列表
     * @return 用户信息列表
     */
    @Select({
            "<script>",
            "SELECT id, username FROM users WHERE id IN",
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    List<Map<String, Object>> selectUsernamesByIds(@Param("userIds") List<Long> userIds);
}
