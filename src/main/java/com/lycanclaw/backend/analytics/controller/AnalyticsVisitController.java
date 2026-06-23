package com.lycanclaw.backend.analytics.controller;

import com.lycanclaw.backend.analytics.dto.VisitEndRequest;
import com.lycanclaw.backend.analytics.dto.VisitEndResponse;
import com.lycanclaw.backend.analytics.dto.VisitStartRequest;
import com.lycanclaw.backend.analytics.dto.VisitStartResponse;
import com.lycanclaw.backend.analytics.service.AnalyticsVisitService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台访问统计接口。
 * 接收公开页面访问开始和离开时的有效停留时间结算。
 * @author Wreckloud
 * @since 2026-06-04
 */
@RestController
@RequestMapping("/api/analytics/visit")
@Tag(name = "访问统计", description = "前台访问和停留时间采集")
public class AnalyticsVisitController {

    private final AnalyticsVisitService analyticsVisitService;

    public AnalyticsVisitController(AnalyticsVisitService analyticsVisitService) {
        this.analyticsVisitService = analyticsVisitService;
    }

    @Operation(summary = "创建访问记录")
    @PostMapping("/start")
    public ApiResponse<VisitStartResponse> start(@RequestBody VisitStartRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(analyticsVisitService.start(request, servletRequest));
    }

    @Operation(summary = "结算访问停留时间")
    @PostMapping("/end")
    public ApiResponse<VisitEndResponse> end(@RequestBody VisitEndRequest request) {
        return ApiResponse.ok(analyticsVisitService.end(request));
    }
}
