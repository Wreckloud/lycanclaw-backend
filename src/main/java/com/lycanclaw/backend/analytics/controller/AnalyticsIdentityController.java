package com.lycanclaw.backend.analytics.controller;

import com.lycanclaw.backend.analytics.dto.VisitorIdentityDto;
import com.lycanclaw.backend.analytics.dto.WalineIdentityLinkRequest;
import com.lycanclaw.backend.analytics.service.VisitorIdentityService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台访客身份关联接口。
 * 接收 Waline 登录 token，并由后端验证后绑定匿名访客身份。
 * @author Wreckloud
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/api/analytics/identity")
@Tag(name = "访客身份", description = "匿名访客与 Waline 登录身份关联")
public class AnalyticsIdentityController {

    private final VisitorIdentityService visitorIdentityService;

    public AnalyticsIdentityController(VisitorIdentityService visitorIdentityService) {
        this.visitorIdentityService = visitorIdentityService;
    }

    @Operation(summary = "关联 Waline 身份")
    @PostMapping("/waline")
    public ApiResponse<VisitorIdentityDto> linkWaline(@RequestBody WalineIdentityLinkRequest request) {
        return ApiResponse.ok(visitorIdentityService.linkWaline(request));
    }
}
