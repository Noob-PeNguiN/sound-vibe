package com.soundvibe.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soundvibe.auth.domain.entity.User;
import com.soundvibe.auth.enums.UserRole;
import com.soundvibe.auth.mapper.UserMapper;
import com.soundvibe.auth.model.dto.AuthDTO;
import com.soundvibe.auth.model.dto.LoginResponseDTO;
import com.soundvibe.auth.service.UserService;
import com.soundvibe.auth.util.JwtUtil;
import com.soundvibe.common.exception.BizException;
import com.soundvibe.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 用户服务实现类
 * 实现用户注册、登录等核心业务逻辑
 *
 * @author SoundVibe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    /**
     * 用户注册
     * <p>
     * 安全措施:
     * - 用户名查重（防止重复注册）
     * - BCrypt 密码加密（防止明文存储）
     * - 事务保证（确保数据一致性）
     *
     * @param authDTO 注册请求数据
     * @throws BizException 用户名已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(AuthDTO authDTO) {
        var username = authDTO.getUsername();
        var password = authDTO.getPassword();

        // Step 1: 查重 - 使用 LambdaQueryWrapper 防止 SQL 注入
        var queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username);
        var existingUser = userMapper.selectOne(queryWrapper);

        if (Objects.nonNull(existingUser)) {
            log.warn("用户注册失败: 用户名 [{}] 已存在", username);
            throw new BizException(ResultCode.USER_ALREADY_EXISTS);
        }

        // Step 2: 密码加密 - 使用 Hutool BCrypt
        var passwordHash = BCrypt.hashpw(password);

        // Step 3: 构建用户实体并落库
        var user = new User();
        user.setUsername(username);
        user.setPassword(passwordHash);
        user.setRole(UserRole.PRODUCER.getCode());

        var inserted = userMapper.insert(user);
        if (inserted <= 0) {
            log.error("用户注册失败: 数据库插入失败，用户名 [{}]", username);
            throw new BizException("注册失败，请稍后重试");
        }

        log.info("用户注册成功: 用户名 [{}], ID [{}]", username, user.getId());
    }

    /**
     * 用户登录
     * <p>
     * 安全措施:
     * - BCrypt 密码校验（防止密码泄露）
     * - JWT Token 生成（无状态认证）
     *
     * @param authDTO 登录请求数据
     * @return 登录响应（包含 Token 和用户信息）
     * @throws BizException 用户不存在或密码错误时抛出
     */
    @Override
    public LoginResponseDTO login(AuthDTO authDTO) {
        var username = authDTO.getUsername();
        var password = authDTO.getPassword();

        // Step 1: 根据用户名查询用户 - 使用 LambdaQueryWrapper
        var queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username);
        var user = userMapper.selectOne(queryWrapper);

        if (Objects.isNull(user)) {
            log.warn("用户登录失败: 用户名 [{}] 不存在", username);
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }

        // Step 2: 校验密码 - 使用 BCrypt
        var isPasswordCorrect = BCrypt.checkpw(password, user.getPassword());
        if (!isPasswordCorrect) {
            log.warn("用户登录失败: 用户名 [{}] 密码错误", username);
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }

        // Step 3: 生成 JWT Token
        var token = JwtUtil.generateToken(user.getId(), user.getRole());
        log.info("用户登录成功: 用户名 [{}], ID [{}], 角色 [{}]", username, user.getId(), user.getRole());

        // Step 4: 构建登录响应
        return LoginResponseDTO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}
