package com.soundvibe.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soundvibe.auth.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 * 提供用户表的数据访问操作
 *
 * @author SoundVibe Team
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // MyBatis-Plus 已提供 CRUD 基础方法
    // 如需自定义 SQL，可在此添加方法声明
}
