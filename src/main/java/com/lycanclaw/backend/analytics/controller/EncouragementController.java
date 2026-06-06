package com.lycanclaw.backend.analytics.controller;

import com.lycanclaw.backend.analytics.dto.EncouragementSettleRequest;
import com.lycanclaw.backend.analytics.dto.EncouragementSettleResponse;
import com.lycanclaw.backend.analytics.service.EncouragementService;
import com.lycanclaw.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页催更接口。
 * 接收前端连续点击后的批量结算增量，不影响前台个人计数和动画。
 * @author Wreckloud
 * @since 2026-06-04
 */
@RestController
@RequestMapping("/api/encouragement")
@Tag(name = "首页催更", description = "首页催更批量结算")
public class EncouragementController {

    private final EncouragementService encouragementService;

    public EncouragementController(EncouragementService encouragementService) {
        this.encouragementService = encouragementService;
    }

    @Operation(summary = "结算一轮首页催更")
    @PostMapping("/settle")
    public ApiResponse<EncouragementSettleResponse> settle(
            @RequestBody EncouragementSettleRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(encouragementService.settle(request, servletRequest));
    }
}
