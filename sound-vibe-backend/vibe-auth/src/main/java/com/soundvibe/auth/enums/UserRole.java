package com.soundvibe.auth.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举
 * 定义系统中所有可用的用户角色
 *
 * @author SoundVibe Team
 */
@Getter
@AllArgsConstructor
public enum UserRole {

    /**
     * 制作人（普通用户）
     */
    PRODUCER("PRODUCER", "制作人"),

    /**
     * 管理员
     */
    ADMIN("ADMIN", "管理员");

    /**
     * 角色代码（存储在数据库）
     */
    private final String code;

    /**
     * 角色描述
     */
    private final String description;
}
