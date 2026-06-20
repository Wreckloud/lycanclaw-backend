package com.lycanclaw.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicWriteRateLimitInterceptorTest {

    @Test
    void limitsPostRequestsButAllowsReads() throws Exception {
        PublicWriteRateLimitInterceptor interceptor = new PublicWriteRateLimitInterceptor(
                new ClientIpResolver(),
                new InMemorySlidingWindowRateLimiter(),
                new ApiErrorResponseWriter(new ObjectMapper())
        );
        ReflectionTestUtils.setField(interceptor, "rateLimitPerMinute", 2);

        assertTrue(interceptor.preHandle(request("GET"), new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request("POST"), new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request("POST"), new MockHttpServletResponse(), new Object()));

        MockHttpServletResponse limitedResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request("POST"), limitedResponse, new Object()));
        assertEquals(429, limitedResponse.getStatus());
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/analytics/visit/start");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
