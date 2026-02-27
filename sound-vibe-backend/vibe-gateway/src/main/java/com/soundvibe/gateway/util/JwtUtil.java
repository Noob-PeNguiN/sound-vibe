package com.soundvibe.gateway.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Gateway 专用 JWT 工具类
 * <p>
 * 使用 Hutool JWT 实现 Token 验证与解析，算法和密钥与 vibe-auth 模块保持一致。
 * <p>
 * ⚠️ 为什么不复用 vibe-common？
 * Gateway 基于 WebFlux (Netty)，而 vibe-common 传递了 mybatis-plus / mysql-connector
 * 等 Servlet 相关依赖，会导致类路径冲突。因此 Gateway 独立维护一份轻量级 JwtUtil。
 * 后续可将 JWT 逻辑提取到独立的 vibe-jwt-core 模块（无 Servlet 依赖）供所有服务共享。
 *
 * @author SoundVibe Team
 */
@Slf4j
public class JwtUtil {

    /**
     * JWT 密钥（必须与 vibe-auth 的 JwtUtil.SECRET 保持一致）
     */
    private static final String SECRET = "soundvibe-secret-2026";

    /**
     * 验证 Token 是否有效（签名校验）
     *
     * @param token JWT Token 字符串
     * @return true=签名有效, false=无效或异常
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
     * 解析 Token
     *
     * @param token JWT Token 字符串
     * @return Hutool JWT 对象
     */
    public static JWT parseToken(String token) {
        return JWTUtil.parseToken(token);
    }

    /**
     * 从 Token 中提取用户 ID
     *
     * @param token JWT Token 字符串
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
     * @param token JWT Token 字符串
     * @return 用户角色字符串
     */
    public static String getRole(String token) {
        JWT jwt = parseToken(token);
        return jwt.getPayload("role").toString();
    }

    /**
     * 判断 Token 是否已过期
     *
     * @param token JWT Token 字符串
     * @return true=已过期, false=未过期
     */
    public static boolean isExpired(String token) {
        try {
            JWT jwt = parseToken(token);
            Object expObj = jwt.getPayload("exp");
            long exp = expObj instanceof Integer
                    ? ((Integer) expObj).longValue()
                    : (Long) expObj;
            return System.currentTimeMillis() > exp;
        } catch (Exception e) {
            log.error("解析 Token 过期时间失败: {}", e.getMessage());
            return true;
        }
    }
}
