package com.ybc.ybc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@EnableJpaAuditing
@ConfigurationPropertiesScan
public class YbcApplication {

	public static void main(String[] args) {
		SpringApplication.run(YbcApplication.class, args);
	}

}
