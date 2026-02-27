package com.soundvibe.auth.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 * 认证传输对象
 * 用于注册和登录请求
 *
 * @author SoundVibe Team
 */
@Data
public class AuthDTO implements Serializable {

    /**
     * 用户名
     * 必填，长度 3-20 字符
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在 3-20 个字符之间")
    private String username;

    /**
     * 密码
     * 必填，长度 6-50 字符（明文传输，后端加密存储）
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度必须在 6-50 个字符之间")
    private String password;
}
