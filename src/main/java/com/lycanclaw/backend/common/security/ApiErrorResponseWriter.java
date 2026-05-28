package com.lycanclaw.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lycanclaw.backend.common.api.ApiResponse;
import com.lycanclaw.backend.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 拦截器统一错误响应写入器
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, ErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.fail(errorCode)
        );
    }
}
