package com.jejo.satchel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class LoanServiceConfig {

	@Bean
	ThreadPoolTaskScheduler loanClosingTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1); // Just 1 thread is enough for simple tasks
		scheduler.setThreadNamePrefix("loan-closing-scheduler-");
		return scheduler;
	}
}
