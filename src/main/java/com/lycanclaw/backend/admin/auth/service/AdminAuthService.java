package com.lycanclaw.backend.admin.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lycanclaw.backend.admin.auth.dto.AdminAuthMeDto;
import com.lycanclaw.backend.admin.auth.dto.AdminAuthSessionDto;
import com.lycanclaw.backend.common.security.AdminAuthPrincipal;
import com.lycanclaw.backend.common.security.AdminTokenAuthService;
import com.lycanclaw.backend.waline.service.WalineGatewayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提供AdminAuth相关业务能力。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class AdminAuthService {

    private static final Pattern QQ_EMAIL_PATTERN = Pattern.compile("^(\\d{5,12})@qq\\.com$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QQ_URL_PATTERN = Pattern.compile("(?:qzone\\.qq\\.com|user\\.qzone\\.qq\\.com)/(\\d{5,12})", Pattern.CASE_INSENSITIVE);

    private final WalineGatewayClient walineGatewayClient;
    private final AdminSessionService adminSessionService;
    private final AdminTokenAuthService adminTokenAuthService;

    @Value("${lycan.security.admin-require-waline-administrator:true}")
    private boolean requireWalineAdministrator;

    @Value("${lycan.security.admin-qq-whitelist:}")
    private String adminQqWhitelistRaw;

    public AdminAuthService(
            WalineGatewayClient walineGatewayClient,
            AdminSessionService adminSessionService,
            AdminTokenAuthService adminTokenAuthService
    ) {
        this.walineGatewayClient = walineGatewayClient;
        this.adminSessionService = adminSessionService;
        this.adminTokenAuthService = adminTokenAuthService;
    }

    /**
     * 使用 Waline token 交换后端管理会话。
     */
    public AdminAuthSessionDto exchangeWalineToken(String walineToken) {
        if (walineToken == null || walineToken.isBlank()) {
            throw new IllegalArgumentException("walineToken 不能为空");
        }

        JsonNode user = walineGatewayClient.fetchTokenProfile(walineToken.trim());
        String role = pickText(user, "type");
        if (requireWalineAdministrator && !"administrator".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException("Waline 身份不是管理员，拒绝换取管理会话");
        }

        String nickname = pickText(user, "nick", "display_name");
        String email = pickText(user, "mail", "email");
        String userId = pickText(user, "objectId", "id");
        String qq = resolveQq(user, email);
        checkQqWhitelist(qq);

        AdminAuthPrincipal principal = new AdminAuthPrincipal(
                "session",
                userId,
                nickname.isBlank() ? "Waline管理员" : nickname,
                email,
                qq,
                role.isBlank() ? "administrator" : role,
                ""
        );
        AdminSessionService.SessionToken session = adminSessionService.createSession(principal);
        AdminAuthPrincipal sessionPrincipal = session.principal();

        return new AdminAuthSessionDto(
                session.token(),
                sessionPrincipal.mode(),
                sessionPrincipal.userId(),
                sessionPrincipal.nickname(),
                sessionPrincipal.email(),
                sessionPrincipal.qq(),
                sessionPrincipal.role(),
                sessionPrincipal.expiresAt()
        );
    }

    /**
     * 查询当前管理员身份。
     */
    public Optional<AdminAuthMeDto> currentAdmin(String adminToken) {
        return adminTokenAuthService.authenticate(adminToken)
                .map(this::toMeDto);
    }

    /**
     * 注销后端会话（静态 token 不会被移除）。
     */
    public void logout(String adminToken) {
        adminTokenAuthService.revokeSession(adminToken);
    }

    /**
     * 管理员主体转换为 /me 输出 DTO。
     */
    public AdminAuthMeDto toMeDto(AdminAuthPrincipal principal) {
        return new AdminAuthMeDto(
                true,
                principal.mode(),
                principal.userId(),
                principal.nickname(),
                principal.email(),
                principal.qq(),
                principal.role(),
                principal.expiresAt()
        );
    }

    private void checkQqWhitelist(String qq) {
        Set<String> whitelist = parseWhitelist(adminQqWhitelistRaw);
        if (whitelist.isEmpty()) {
            throw new IllegalArgumentException("未配置 lycan.security.admin-qq-whitelist");
        }
        if (qq.isBlank() || !whitelist.contains(qq)) {
            throw new IllegalArgumentException("当前 Waline 账号不在管理员 QQ 白名单中");
        }
    }

    private Set<String> parseWhitelist(String rawValue) {
        Set<String> values = new LinkedHashSet<>();
        if (rawValue == null || rawValue.isBlank()) {
            return values;
        }
        for (String item : rawValue.split(",")) {
            String value = item == null ? "" : item.trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private String resolveQq(JsonNode user, String email) {
        List<String> candidates = new ArrayList<>();
        String directQq = pickText(user, "qq");
        if (!directQq.isBlank()) {
            candidates.add(directQq);
        }
        if (!email.isBlank()) {
            Matcher matcher = QQ_EMAIL_PATTERN.matcher(email);
            if (matcher.find()) {
                candidates.add(matcher.group(1));
            }
        }
        String url = pickText(user, "link", "url");
        if (!url.isBlank()) {
            Matcher matcher = QQ_URL_PATTERN.matcher(url);
            if (matcher.find()) {
                candidates.add(matcher.group(1));
            }
        }
        return candidates.stream().filter(value -> !value.isBlank()).findFirst().orElse("");
    }

    private String pickText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }
}
