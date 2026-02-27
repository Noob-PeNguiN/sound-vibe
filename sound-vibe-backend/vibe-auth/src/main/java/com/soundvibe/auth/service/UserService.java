package com.soundvibe.auth.service;

import com.soundvibe.auth.model.dto.AuthDTO;
import com.soundvibe.auth.model.dto.LoginResponseDTO;

/**
 * 用户服务接口
 * 定义用户认证相关业务逻辑
 *
 * @author SoundVibe Team
 */
public interface UserService {

    /**
     * 用户注册
     * <p>
     * 业务流程:
     * 1. 校验用户名是否已存在
     * 2. 使用 BCrypt 加密密码
     * 3. 保存用户到数据库（默认角色为 PRODUCER）
     *
     * @param authDTO 注册请求数据
     * @throws com.soundvibe.common.exception.BizException 用户名已存在时抛出
     */
    void register(AuthDTO authDTO);

    /**
     * 用户登录
     * <p>
     * 业务流程:
     * 1. 根据用户名查询用户
     * 2. 使用 BCrypt 校验密码
     * 3. 生成 JWT Token
     * 4. 返回用户信息和 Token
     *
     * @param authDTO 登录请求数据
     * @return 登录响应（包含 Token 和用户信息）
     * @throws com.soundvibe.common.exception.BizException 用户不存在或密码错误时抛出
     */
    LoginResponseDTO login(AuthDTO authDTO);
}
