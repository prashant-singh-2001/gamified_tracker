package com.tracker.activity;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// Issue #82: OutboxRelay's poller must not run concurrently across multiple instances.
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
//@EnableFeignClients
public class ActivityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivityServiceApplication.class, args);
    }

}
