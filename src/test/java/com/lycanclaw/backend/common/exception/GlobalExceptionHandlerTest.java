package com.lycanclaw.backend.common.exception;

import com.lycanclaw.backend.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器测试。
 * 验证请求格式错误统一返回稳定的400响应。
 * @author Wreckloud
 * @since 2026-06-24
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/test/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("请求参数格式错误"));
    }

    @Test
    void returnsBadRequestForMissingOrInvalidParameter() throws Exception {
        mockMvc.perform(get("/test/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/test/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/test/number").param("value", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @RestController
    private static class TestController {

        @PostMapping("/test/json")
        ApiResponse<String> json(@RequestBody TestRequest request) {
            return ApiResponse.ok(request.value());
        }

        @GetMapping("/test/number")
        ApiResponse<Integer> number(@RequestParam int value) {
            return ApiResponse.ok(value);
        }

        @GetMapping("/test/header")
        ApiResponse<String> header(@RequestHeader("X-Test") String value) {
            return ApiResponse.ok(value);
        }
    }

    private record TestRequest(String value) {
    }
}
