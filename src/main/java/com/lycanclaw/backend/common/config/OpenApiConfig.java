package com.lycanclaw.backend.common.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 与 Knife4j 文档配置
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@Configuration
@SecurityScheme(
        name = "adminToken",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-Lycan-Admin-Token",
        description = "管理员凭证（静态 token 或 /api/admin/auth/waline/exchange 返回的会话 token）"
)
public class OpenApiConfig {

    /**
     * 定义全局文档元信息。
     */
    @Bean
    public OpenAPI lycanClawOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LycanClaw Backend API")
                .version("v1")
                .description("LycanClaw 个人博客后端接口文档")
                .contact(new Contact()
                        .name("Wreckloud")
                        .url("https://www.wreckloud.com")));
    }
}
