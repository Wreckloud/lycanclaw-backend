package com.lycanclaw.backend.admin.auth.controller;

import com.lycanclaw.backend.admin.auth.dto.AdminAuthMeDto;
import com.lycanclaw.backend.admin.auth.dto.AdminAuthSessionDto;
import com.lycanclaw.backend.admin.auth.dto.AdminWalineExchangeRequest;
import com.lycanclaw.backend.admin.auth.service.AdminAuthService;
import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.common.api.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员会话鉴权接口。
 *
 * @author Wreckloud
 * @since 2026-05-28
 */
@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "管理员鉴权", description = "Waline 身份换取后端管理会话")
public class AdminAuthController {

    private static final String ADMIN_TOKEN_HEADER = "X-Lycan-Admin-Token";

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    /**
     * 用 Waline 登录 token 换取后端会话 token。
     */
    @Operation(summary = "Waline 登录换取管理会话")
    @PostMapping("/waline/exchange")
    public ApiResponse<AdminAuthSessionDto> exchange(@RequestBody AdminWalineExchangeRequest request) {
        return ApiResponse.ok(adminAuthService.exchangeWalineToken(request.walineToken()));
    }

    /**
     * 返回当前请求携带凭证对应的管理员身份。
     */
    @Operation(summary = "查看当前管理会话")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AdminAuthMeDto>> me(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        return adminAuthService.currentAdmin(token)
                .map(dto -> ResponseEntity.ok(ApiResponse.ok(dto)))
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.fail(ErrorCode.ADMIN_TOKEN_INVALID)));
    }

    /**
     * 退出当前会话（仅会话 token 生效，静态 token 为无状态）。
     */
    @Operation(summary = "退出当前管理会话")
    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        adminAuthService.logout(token);
        return ApiResponse.ok(Map.of("success", true));
    }
}
