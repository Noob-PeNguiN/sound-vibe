package com.soundvibe.auth.controller;

import com.soundvibe.auth.model.dto.AuthDTO;
import com.soundvibe.auth.model.dto.LoginResponseDTO;
import com.soundvibe.auth.service.UserService;
import com.soundvibe.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 提供用户注册、登录接口
 *
 * @author SoundVibe Team
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 用户注册接口
     * <p>
     * POST /auth/register
     * <p>
     * 请求体示例:
     * {
     *   "username": "producer01",
     *   "password": "pass123456"
     * }
     *
     * @param authDTO 注册请求数据（自动校验）
     * @return 注册结果（无数据返回）
     */
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody AuthDTO authDTO) {
        log.info("收到注册请求: 用户名 [{}]", authDTO.getUsername());
        userService.register(authDTO);
        return Result.success();
    }

    /**
     * 用户登录接口
     * <p>
     * POST /auth/login
     * <p>
     * 请求体示例:
     * {
     *   "username": "producer01",
     *   "password": "pass123456"
     * }
     *
     * @param authDTO 登录请求数据（自动校验）
     * @return 登录响应（包含 Token 和用户信息）
     */
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@Validated @RequestBody AuthDTO authDTO) {
        log.info("收到登录请求: 用户名 [{}]", authDTO.getUsername());
        var loginResponse = userService.login(authDTO);
        return Result.success(loginResponse);
    }
}
