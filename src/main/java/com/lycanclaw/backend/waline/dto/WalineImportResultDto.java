package com.lycanclaw.backend.waline.dto;

import java.util.List;

/**
 * Waline 数据导入结果。
 * 用于后台展示本次导入涉及的数据表、成功行数和跳过的数据表。
 * @author Wreckloud
 * @since 2026-06-17
 */
public record WalineImportResultDto(
        int importedTables,
        int importedRows,
        List<String> skippedTables
) {
}
