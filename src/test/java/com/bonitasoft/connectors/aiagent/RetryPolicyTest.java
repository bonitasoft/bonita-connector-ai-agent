package com.bonitasoft.connectors.aiagent;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class RetryPolicyTest {

    @Test
    void shouldReturnImmediatelyOnSuccess() throws AiAgentException {
        RetryPolicy policy = new RetryPolicy(3);
        String result = policy.execute(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldRetryOnRetryableError() throws AiAgentException {
        AtomicInteger attempts = new AtomicInteger(0);
        // Override sleep to avoid actual waiting in tests
        RetryPolicy policy = new RetryPolicy(3) {
            @Override
            void sleep(long millis) {
                // no-op for tests
            }
        };

        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new AiAgentException("Rate limited", 429, true);
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryOnNonRetryableError() {
        RetryPolicy policy = new RetryPolicy(3) {
            @Override
            void sleep(long millis) {
                // no-op for tests
            }
        };

        assertThatThrownBy(() -> policy.execute(() -> {
            throw new AiAgentException("Unauthorized", 401, false);
        })).isInstanceOf(AiAgentException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    void shouldExhaustRetriesAndThrow() {
        RetryPolicy policy = new RetryPolicy(2) {
            @Override
            void sleep(long millis) {
                // no-op for tests
            }
        };

        assertThatThrownBy(() -> policy.execute(() -> {
            throw new AiAgentException("Server error", 500, true);
        })).isInstanceOf(AiAgentException.class)
                .hasMessageContaining("Server error");
    }

    @Test
    void shouldIdentifyRetryableStatusCodes() {
        assertThat(RetryPolicy.isRetryableStatusCode(429)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(500)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(502)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(503)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(401)).isFalse();
        assertThat(RetryPolicy.isRetryableStatusCode(403)).isFalse();
        assertThat(RetryPolicy.isRetryableStatusCode(404)).isFalse();
    }

    @Test
    void shouldCalculateExponentialBackoff() {
        RetryPolicy policy = new RetryPolicy(5);
        // attempt 0: base ~1s, attempt 1: ~2s, attempt 2: ~4s
        long wait0 = policy.calculateWait(0);
        long wait1 = policy.calculateWait(1);
        long wait2 = policy.calculateWait(2);

        assertThat(wait0).isBetween(1000L, 1500L);
        assertThat(wait1).isBetween(2000L, 3000L);
        assertThat(wait2).isBetween(4000L, 6000L);
    }
}
