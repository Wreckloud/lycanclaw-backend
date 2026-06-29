package com.lycanclaw.backend.common.security;

/**
 * 管理员鉴权常量。
 * @author Wreckloud
 * @since 2026-05-28
 */
public final class AdminAuthConstants {

    /**
     * 管理员凭证请求头。
     */
    public static final String ADMIN_TOKEN_HEADER = "X-Lycan-Admin-Token";

    /**
     * 拦截器在 request 中写入的管理员主体属性键。
     */
    public static final String ADMIN_PRINCIPAL_ATTR = "LYCAN_ADMIN_PRINCIPAL";

    /**
     * Waline 身份换取管理会话的公开入口。
     */
    public static final String WALINE_EXCHANGE_PATH = "/api/admin/auth/waline/exchange";

    private AdminAuthConstants() {
    }
}
