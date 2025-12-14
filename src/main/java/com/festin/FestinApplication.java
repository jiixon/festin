package com.festin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = "com.festin")
@EnableJpaAuditing
public class FestinApplication {

	public static void main(String[] args) {
		SpringApplication.run(FestinApplication.class, args);
	}

}
