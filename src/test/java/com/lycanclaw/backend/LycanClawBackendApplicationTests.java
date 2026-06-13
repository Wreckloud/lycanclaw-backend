package com.lycanclaw.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 后端应用上下文启动测试。
 *
 * @author Wreckloud
 * @since 2026-05-15
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lycanclaw;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "lycan.recommendation.startup-warmup-enabled=false"
})
class LycanClawBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
