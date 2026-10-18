package com.tracker.gamification;

import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class GamificationServiceApplicationTests {

	// @EnableSchedulerLock needs a real LockProvider, which needs a "shedlock" table --
	// the H2 test schema (create-drop from entities, spring.sql.init.mode=never) has none.
	// Mockito returns Optional.empty() from the unstubbed lock() call, which ShedLock reads
	// as "lock not acquired", so RankRecomputeServiceImpl's @Scheduled tick is silently
	// skipped during this context test instead of failing on a missing table.
	@MockBean
	private LockProvider lockProvider;

	@Test
	void contextLoads() {
	}

}
