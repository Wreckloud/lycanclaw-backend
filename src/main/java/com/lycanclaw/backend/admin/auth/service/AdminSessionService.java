package com.lycanclaw.backend.admin.auth.service;

import com.lycanclaw.backend.common.security.AdminAuthPrincipal;
import com.lycanclaw.backend.common.time.AppTimeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提供AdminSession相关业务能力。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class AdminSessionService {

    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, SessionEntry> sessionStore = new ConcurrentHashMap<>();
    private final AppTimeProvider appTimeProvider;

    @Value("${lycan.security.admin-session-ttl-seconds:28800}")
    private long sessionTtlSeconds;

    @Value("${lycan.security.admin-session-max-size:200}")
    private int maxSessionSize;

    public AdminSessionService(AppTimeProvider appTimeProvider) {
        this.appTimeProvider = appTimeProvider;
    }

    /**
     * 创建新的管理员会话，返回会话令牌与主体信息。
     */
    public SessionToken createSession(AdminAuthPrincipal principal) {
        cleanupExpired();
        shrinkIfNeeded();

        String token = generateToken();
        Instant expiresAt = Instant.now().plus(Duration.ofSeconds(Math.max(60, sessionTtlSeconds)));
        sessionStore.put(token, new SessionEntry(principal, expiresAt));

        AdminAuthPrincipal sessionPrincipal = new AdminAuthPrincipal(
                "session",
                principal.userId(),
                principal.nickname(),
                principal.email(),
                principal.qq(),
                principal.role(),
                appTimeProvider.toOffsetString(expiresAt)
        );
        return new SessionToken(token, sessionPrincipal);
    }

    /**
     * 校验会话令牌；过期会自动清理。
     */
    public Optional<AdminAuthPrincipal> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        SessionEntry entry = sessionStore.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            sessionStore.remove(token);
            return Optional.empty();
        }
        AdminAuthPrincipal source = entry.principal();
        return Optional.of(new AdminAuthPrincipal(
                "session",
                source.userId(),
                source.nickname(),
                source.email(),
                source.qq(),
                source.role(),
                appTimeProvider.toOffsetString(entry.expiresAt())
        ));
    }

    /**
     * 主动注销会话。
     */
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionStore.remove(token);
    }

    private String generateToken() {
        byte[] bytes = new byte[36];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        sessionStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private void shrinkIfNeeded() {
        int limit = Math.max(20, maxSessionSize);
        if (sessionStore.size() < limit) {
            return;
        }
        int removeCount = Math.max(1, sessionStore.size() - limit + 1);
        sessionStore.keySet().stream().limit(removeCount).forEach(sessionStore::remove);
    }

    /**
     * 会话令牌与主体。
     */
    public record SessionToken(String token, AdminAuthPrincipal principal) {
    }

    /**
     * 会话存储实体。
     */
    private record SessionEntry(AdminAuthPrincipal principal, Instant expiresAt) {
    }
}
