package com.quizverse.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Entities and repositories live outside this class's package, so JPA needs pointing at them.
@SpringBootApplication(scanBasePackages = "com.quizverse")
@EntityScan("com.quizverse.model")
@EnableJpaRepositories("com.quizverse.repository")
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}
