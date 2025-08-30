package com.quizmailer.demo;

import com.quizmailer.demo.config.TestJpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Import(TestJpaConfig.class)
class DemoApplicationTests {

	@Autowired
	private JavaMailSender javaMailSender;

	// @Test
	// void contextLoads() {
	// 	assertNotNull(javaMailSender, "JavaMailSender should be autowired successfully");
	// }

}
