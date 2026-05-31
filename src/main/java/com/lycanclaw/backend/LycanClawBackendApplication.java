package com.lycanclaw.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 后端应用启动入口。
 * 用于启动 Spring Boot 服务。
 * @author Wreckloud
 * @since 2026-05-15
 */
@SpringBootApplication
@EnableScheduling
public class LycanClawBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LycanClawBackendApplication.class, args);
    }
}
