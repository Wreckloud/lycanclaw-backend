package com.lycanclaw.backend.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Waline 邮件通知配置状态。
 * 仅展示 SMTP 和通知能力是否配置，不返回任何密码或密钥。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Schema(description = "Waline 邮件通知配置状态")
public record WalineNotificationStatusDto(
        boolean smtpConfigured,
        boolean authorEmailConfigured,
        boolean commentAuditEnabled,
        boolean authorNotificationEnabled
) {
}
