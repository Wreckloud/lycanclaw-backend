package com.lycanclaw.backend.admin.controller;

import com.lycanclaw.backend.admin.dto.AdminOperationalConfigDto;
import com.lycanclaw.backend.admin.service.AdminOperationalConfigService;
import com.lycanclaw.backend.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/config/operations")
public class AdminOperationalConfigController {

    private final AdminOperationalConfigService operationalConfigService;

    public AdminOperationalConfigController(AdminOperationalConfigService operationalConfigService) {
        this.operationalConfigService = operationalConfigService;
    }

    @GetMapping
    public ApiResponse<AdminOperationalConfigDto> getOperationalConfig() {
        return ApiResponse.ok(operationalConfigService.view());
    }
}
