package ai.xdigit.ragchatstorage.security;

import ai.xdigit.ragchatstorage.config.RateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private RateLimitService service(int capacity, Duration window) {
        RateLimitProperties props = new RateLimitProperties();
        props.setCapacity(capacity);
        props.setWindow(window);
        return new RateLimitService(props);
    }

    @Test
    void firstRequestIsAllowed() {
        RateLimitService svc = service(3, Duration.ofMinutes(1));
        RateLimitDecision d = svc.tryConsume("key-a");

        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(2);
        assertThat(d.retryAfterSeconds()).isZero();
    }

    @Test
    void remainingDecrementsWithEachRequest() {
        RateLimitService svc = service(3, Duration.ofMinutes(1));

        svc.tryConsume("key");
        RateLimitDecision second = svc.tryConsume("key");

        assertThat(second.remaining()).isEqualTo(1);
    }

    @Test
    void requestAtExactCapacityIsAllowedWithZeroRemaining() {
        RateLimitService svc = service(2, Duration.ofMinutes(1));

        svc.tryConsume("key");
        RateLimitDecision last = svc.tryConsume("key");

        assertThat(last.allowed()).isTrue();
        assertThat(last.remaining()).isZero();
    }

    @Test
    void requestBeyondCapacityIsRejected() {
        RateLimitService svc = service(2, Duration.ofMinutes(1));

        svc.tryConsume("key");
        svc.tryConsume("key");
        RateLimitDecision rejected = svc.tryConsume("key");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void differentKeysHaveIndependentBuckets() {
        RateLimitService svc = service(1, Duration.ofMinutes(1));

        RateLimitDecision a = svc.tryConsume("key-a");
        RateLimitDecision b = svc.tryConsume("key-b");

        assertThat(a.allowed()).isTrue();
        assertThat(b.allowed()).isTrue();
    }

    @Test
    void windowResetsAfterExpiry() throws InterruptedException {
        RateLimitService svc = service(1, Duration.ofMillis(50));

        svc.tryConsume("key");
        RateLimitDecision rejected = svc.tryConsume("key");
        assertThat(rejected.allowed()).isFalse();

        Thread.sleep(60);

        RateLimitDecision afterReset = svc.tryConsume("key");
        assertThat(afterReset.allowed()).isTrue();
    }

    @Test
    void getCapacityReturnsConfiguredValue() {
        RateLimitService svc = service(42, Duration.ofMinutes(1));
        assertThat(svc.getCapacity()).isEqualTo(42);
    }
}
