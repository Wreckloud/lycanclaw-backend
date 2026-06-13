package com.lycanclaw.backend.comment.service;

import com.lycanclaw.backend.comment.dto.WalineNotificationStatusDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Waline 邮件通知状态服务。
 * 只判断通知相关环境变量是否配置，不读取或返回 SMTP 密码。
 * @author Wreckloud
 * @since 2026-06-09
 */
@Service
public class WalineNotificationStatusService {

    @Value("${lycan.waline.notification.smtp-host:}")
    private String smtpHost;

    @Value("${lycan.waline.notification.smtp-service:}")
    private String smtpService;

    @Value("${lycan.waline.notification.smtp-user:}")
    private String smtpUser;

    @Value("${lycan.waline.notification.smtp-pass:}")
    private String smtpPass;

    @Value("${lycan.waline.notification.author-email:}")
    private String authorEmail;

    @Value("${lycan.waline.notification.comment-audit:false}")
    private boolean commentAudit;

    @Value("${lycan.waline.notification.disable-author-notify:false}")
    private boolean disableAuthorNotify;

    /**
     * 返回邮件通知能力的脱敏配置状态。
     */
    public WalineNotificationStatusDto status() {
        return new WalineNotificationStatusDto(
                (configured(smtpHost) || configured(smtpService))
                        && configured(smtpUser)
                        && configured(smtpPass),
                configured(authorEmail),
                commentAudit,
                !disableAuthorNotify
        );
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank();
    }
}
