package com.tracker.gamification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Required for RankRecomputeServiceImpl's @Scheduled recompute() — without this,
// @Scheduled methods are silently never invoked.
@EnableScheduling
@SpringBootApplication
public class GamificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GamificationServiceApplication.class, args);
	}

}
