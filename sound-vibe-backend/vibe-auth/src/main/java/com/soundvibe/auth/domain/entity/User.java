package com.soundvibe.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.soundvibe.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体
 * 对应数据库表 users
 *
 * @author SoundVibe Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("users")
public class User extends BaseEntity {

    /**
     * 用户名（唯一）
     */
    @TableField("username")
    private String username;

    /**
     * 密码哈希值（BCrypt 加密）
     */
    @TableField("password_hash")
    private String password;

    /**
     * 用户角色
     * 枚举值: PRODUCER, ADMIN
     */
    @TableField("role")
    private String role;
}
