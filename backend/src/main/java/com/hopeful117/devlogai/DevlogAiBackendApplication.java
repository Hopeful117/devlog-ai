package com.hopeful117.devlogai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DevlogAiBackendApplication {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean(name = "aiCredentialRevocationTaskScheduler")
	ThreadPoolTaskScheduler aiCredentialRevocationTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("ai-credential-revocation-");
		return scheduler;
	}

	public static void main(String[] args) {
		SpringApplication.run(DevlogAiBackendApplication.class, args);
	}

}
