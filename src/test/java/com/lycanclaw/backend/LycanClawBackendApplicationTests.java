package com.lycanclaw.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
/**
 * @Description 后端应用启动测试
 * @Author Wreckloud
 * @Date 2026-05-15
 */
class LycanClawBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
