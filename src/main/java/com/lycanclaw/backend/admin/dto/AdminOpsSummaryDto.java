package com.lycanclaw.backend.admin.dto;

/**
 * 管理端运维检查摘要
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
public record AdminOpsSummaryDto(
        boolean ok,
        String message,
        String checkedAt,
        Object services,
        Object sync
) {
}
