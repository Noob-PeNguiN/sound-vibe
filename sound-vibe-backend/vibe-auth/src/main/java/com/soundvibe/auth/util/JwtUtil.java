package com.soundvibe.auth.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 * 使用 Hutool 实现 JWT 的生成与解析
 *
 * @author SoundVibe Team
 */
@Slf4j
public class JwtUtil {

    /**
     * JWT 密钥（硬编码）
     * 生产环境应从配置文件读取
     */
    private static final String SECRET = "soundvibe-secret-2026";

    /**
     * Token 过期时间（7天）
     */
    private static final long EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000;

    /**
     * 生成 JWT Token
     *
     * @param userId 用户 ID
     * @param role   用户角色
     * @return JWT Token 字符串
     */
    public static String generateToken(Long userId, String role) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", userId);
        payload.put("role", role);
        payload.put("iat", System.currentTimeMillis());
        payload.put("exp", System.currentTimeMillis() + EXPIRE_TIME);

        return JWTUtil.createToken(payload, SECRET.getBytes());
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public static boolean verify(String token) {
        try {
            return JWTUtil.verify(token, SECRET.getBytes());
        } catch (Exception e) {
            log.error("JWT 验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析 Token 获取 Payload
     *
     * @param token JWT Token
     * @return JWT 对象（可获取 Payload 数据）
     */
    public static JWT parseToken(String token) {
        return JWTUtil.parseToken(token);
    }

    /**
     * 从 Token 中提取用户 ID
     *
     * @param token JWT Token
     * @return 用户 ID
     */
    public static Long getUserId(String token) {
        JWT jwt = parseToken(token);
        Object idObj = jwt.getPayload("id");
        if (idObj instanceof Integer) {
            return ((Integer) idObj).longValue();
        }
        return (Long) idObj;
    }

    /**
     * 从 Token 中提取用户角色
     *
     * @param token JWT Token
     * @return 用户角色
     */
    public static String getRole(String token) {
        JWT jwt = parseToken(token);
        return jwt.getPayload("role").toString();
    }

    /**
     * 判断 Token 是否过期
     *
     * @param token JWT Token
     * @return 是否过期
     */
    public static boolean isExpired(String token) {
        try {
            JWT jwt = parseToken(token);
            Object expObj = jwt.getPayload("exp");
            Long exp = expObj instanceof Integer ? 
                    ((Integer) expObj).longValue() : (Long) expObj;
            return System.currentTimeMillis() > exp;
        } catch (Exception e) {
            log.error("解析 Token 过期时间失败: {}", e.getMessage());
            return true;
        }
    }
}
