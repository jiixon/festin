package com.festin.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.festin.app")
@EnableJpaAuditing
@EnableScheduling
public class FestinApplication {

	public static void main(String[] args) {
		SpringApplication.run(FestinApplication.class, args);
	}

}
