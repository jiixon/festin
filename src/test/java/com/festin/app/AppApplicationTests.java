package com.festin.app;

import com.festin.app.config.TestRabbitMQConfig;
import com.festin.app.config.TestSecurityConfig;
import com.festin.app.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestRabbitMQConfig.class, TestSecurityConfig.class})
class AppApplicationTests {

	@Test
	void contextLoads() {
	}

}
