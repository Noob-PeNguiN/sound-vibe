package com.soundvibe.gateway.filter;

import com.soundvibe.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局鉴权过滤器 (The Bouncer)
 * <p>
 * 拦截所有经过 Gateway 的请求，执行 JWT Token 验证。
 * <ul>
 *   <li>白名单路径直接放行（登录、注册、公共资源）</li>
 *   <li>验证 Authorization: Bearer &lt;token&gt; 请求头</li>
 *   <li>校验通过后解析 userId，注入 X-User-Id Header 传递给下游服务</li>
 *   <li>校验失败立即返回 401 Unauthorized</li>
 * </ul>
 *
 * @author SoundVibe Team
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 白名单路径（无需 Token 即可访问）
     */
    private static final List<String> ALLOW_LIST = List.of(
            "/auth/login",
            "/auth/register",
            "/assets/public/**",
            "/assets/file/**",
            "/assets/download/**",
            "/search/**"
    );

    /**
     * Token 前缀
     */
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ① 白名单放行
        if (isAllowed(path)) {
            log.debug("白名单放行: {}", path);
            return chain.filter(exchange);
        }

        // ② 提取 Authorization Header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("请求被拒绝 (缺少/格式错误的 Token): {} {}", request.getMethod(), path);
            return unauthorizedResponse(exchange, "缺少有效的 Authorization Header");
        }

        // ③ 提取 Token (去掉 "Bearer " 前缀)
        String token = authHeader.substring(BEARER_PREFIX.length());

        // ④ 验证 Token 签名
        if (!JwtUtil.verify(token)) {
            log.warn("请求被拒绝 (Token 签名无效): {} {}", request.getMethod(), path);
            return unauthorizedResponse(exchange, "Token 签名无效");
        }

        // ⑤ 检查 Token 是否过期
        if (JwtUtil.isExpired(token)) {
            log.warn("请求被拒绝 (Token 已过期): {} {}", request.getMethod(), path);
            return unauthorizedResponse(exchange, "Token 已过期");
        }

        // ⑥ 解析用户信息并注入 Header
        Long userId = JwtUtil.getUserId(token);
        String role = JwtUtil.getRole(token);

        log.debug("鉴权通过: userId={}, role={}, path={}", userId, role, path);

        // 构建新的请求，添加 X-User-Id 和 X-User-Role Header
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Role", role)
                .build();

        // ⑦ 放行（携带用户信息的增强请求）
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 判断请求路径是否在白名单中
     *
     * @param path 请求路径
     * @return true=在白名单中
     */
    private boolean isAllowed(String path) {
        return ALLOW_LIST.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 构建 401 Unauthorized 响应
     *
     * @param exchange ServerWebExchange
     * @param message  错误描述
     * @return Mono<Void>
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":401,\"message\":\"%s\",\"data\":null}",
                message
        );
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 过滤器优先级（数值越小越先执行）
     * 设为 -1，确保在其他过滤器之前执行鉴权
     */
    @Override
    public int getOrder() {
        return -1;
    }
}
