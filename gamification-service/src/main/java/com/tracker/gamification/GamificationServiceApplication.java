package com.tracker.gamification;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Required for RankRecomputeServiceImpl's @Scheduled recompute() — without this,
// @Scheduled methods are silently never invoked.
@EnableScheduling
// Issue #82: recompute() must not run concurrently across multiple instances.
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
@SpringBootApplication
public class GamificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GamificationServiceApplication.class, args);
	}

}
