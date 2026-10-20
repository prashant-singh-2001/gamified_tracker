package com.tracker.gateway.auth;

import com.tracker.gateway.dto.RegisterRequest;
import com.tracker.gateway.exception.EmailAlreadyRegisteredException;
import com.tracker.gateway.repository.UserRepository;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real service against a real (H2) unique constraint with registrations racing on one
 * address. The mock-based AuthServiceTest pins each branch in isolation; this proves the branches
 * compose: whichever way the threads interleave around the pre-check, exactly one account is
 * created and every loser gets the 409 exception, never a raw constraint violation.
 */
@SpringBootTest
class RegistrationRaceTest {

    // Same hermetic Redis stubbing as ApiGatewayApplicationTests: the rate-limit beans connect to
    // Redis eagerly at startup, which doesn't resolve outside docker-compose.
    @MockBean
    private RedisClient redisClient;

    @MockBean
    private StatefulRedisConnection<String, byte[]> redisConnection;

    @MockBean
    private AsyncProxyManager<String> asyncProxyManager;

    @MockBean
    private ProxyManager<String> proxyManager;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void concurrentRegistrationsOfOneEmailCreateExactlyOneUser() throws Exception {
        int threads = 8;
        // Mixed case and padding: the racers must collide on the NORMALISED address, not just
        // on byte-identical strings.
        String[] variants = {"race@example.com", "Race@Example.com", " RACE@example.com", "race@EXAMPLE.COM "};

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Throwable>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String email = variants[i % variants.length];
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    authService.register(new RegisterRequest("Race", "Condition", email, "secret"));
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown();

        int created = 0;
        int conflicts = 0;
        List<Throwable> unexpected = new ArrayList<>();
        for (Future<Throwable> future : results) {
            Throwable outcome = future.get(30, TimeUnit.SECONDS);
            if (outcome == null) {
                created++;
            } else if (outcome instanceof EmailAlreadyRegisteredException) {
                conflicts++;
            } else {
                unexpected.add(outcome);
            }
        }
        pool.shutdownNow();

        assertThat(unexpected).as("no raw constraint violation or other failure may escape").isEmpty();
        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(threads - 1);
        assertThat(userRepository.findAll().stream()
                .filter(u -> "race@example.com".equals(u.getEmail())))
                .hasSize(1);
    }
}
