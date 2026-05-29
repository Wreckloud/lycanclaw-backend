package com.lycanclaw.backend.common.security;

import com.lycanclaw.backend.admin.auth.service.AdminSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * 安全组件。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class AdminTokenAuthService {

    private final AdminSessionService adminSessionService;

    @Value("${lycan.security.admin-token}")
    private String staticAdminToken;

    public AdminTokenAuthService(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    /**
     * 校验管理员凭证：
     * - 先匹配静态令牌（应急保底）；
     * - 再校验会话令牌（Waline 登录换取）。
     */
    public Optional<AdminAuthPrincipal> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        if (constantTimeEquals(staticAdminToken, token)) {
            return Optional.of(AdminAuthPrincipal.staticToken());
        }
        return adminSessionService.verify(token);
    }

    public void revokeSession(String token) {
        adminSessionService.revoke(token);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8);
        byte[] right = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
