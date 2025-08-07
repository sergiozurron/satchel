package com.jejo.satchel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class SatchelApplication {

	public static void main(String[] args) {
		SpringApplication.run(SatchelApplication.class, args);
	}

}
