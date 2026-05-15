package ai.xdigit.ragchatstorage.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitDecisionTest {

    @Test
    void allowedDecisionHasCorrectFields() {
        RateLimitDecision d = new RateLimitDecision(true, 10, 0);

        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(10);
        assertThat(d.retryAfterSeconds()).isZero();
    }

    @Test
    void rejectedDecisionHasCorrectFields() {
        RateLimitDecision d = new RateLimitDecision(false, 0, 30);

        assertThat(d.allowed()).isFalse();
        assertThat(d.remaining()).isZero();
        assertThat(d.retryAfterSeconds()).isEqualTo(30);
    }
}
