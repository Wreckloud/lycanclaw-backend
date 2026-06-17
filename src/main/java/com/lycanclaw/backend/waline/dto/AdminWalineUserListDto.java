package com.lycanclaw.backend.waline.dto;

import java.util.List;

/**
 * Waline 用户分页结果。
 * 用于后台用户管理页显示当前页用户和分页信息。
 * @author Wreckloud
 * @since 2026-06-17
 */
public record AdminWalineUserListDto(
        int page,
        int totalPages,
        int pageSize,
        int totalCount,
        List<AdminWalineUserDto> data
) {
}
