package com.ybc.ybc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class YbcApplication {

	public static void main(String[] args) {
		SpringApplication.run(YbcApplication.class, args);
	}

}
