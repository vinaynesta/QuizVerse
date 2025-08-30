package com.quizmailer.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.quizmailer")
@EnableScheduling
@EntityScan("com.quizmailer.model")
@EnableJpaRepositories("com.quizmailer.repository")
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
		String red = "\u001B[31m";
        String green = "\u001B[32m";
        String yellow = "\u001B[33m";
        String blue = "\u001B[34m";
        String magenta = "\u001B[35m";
        String cyan = "\u001B[36m";
        String reset = "\u001B[0m";
        
        String welcomeMessage = red + "W" + reset + green + "e" + reset + yellow + "l" + reset + blue + "c" + reset + magenta + "o" + reset + cyan + "m" + reset + red + "e" + reset;
        String nameMessage = green + "V" + reset + yellow + "i" + reset + blue + "n" + reset + magenta + "a" + reset + cyan + "y" + reset + red + " " + reset +
                             yellow + "N" + reset + green + "e" + reset + blue + "s" + reset + magenta + "t" + reset + cyan + "a" + reset;

        System.out.println("\n\n" + welcomeMessage);
        System.out.println(nameMessage);
		System.out.println("Hello Mr.Vinay");
	}
}
