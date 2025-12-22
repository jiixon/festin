package com.festin.app.cucumber;

import com.festin.app.config.TestcontainersConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnableRabbit
@Import(TestcontainersConfiguration.class)
public class CucumberSpringConfiguration {
}