package ai.xdigit.ragchatstorage.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HealthResponseTest {

    @Test
    void isUpReturnsTrueWhenStatusUp() {
        HealthResponse r = new HealthResponse("UP", "UP", "UP", Instant.now());
        assertThat(r.isUp()).isTrue();
    }

    @Test
    void isUpReturnsTrueForLowercaseUp() {
        HealthResponse r = new HealthResponse("up", "UP", "UP", Instant.now());
        assertThat(r.isUp()).isTrue();
    }

    @Test
    void isUpReturnsFalseWhenStatusDown() {
        HealthResponse r = new HealthResponse("DOWN", "UP", "DOWN", Instant.now());
        assertThat(r.isUp()).isFalse();
    }

    @Test
    void recordAccessorsReturnCorrectValues() {
        Instant now = Instant.now();
        HealthResponse r = new HealthResponse("UP", "UP", "DOWN", now);

        assertThat(r.status()).isEqualTo("UP");
        assertThat(r.liveness()).isEqualTo("UP");
        assertThat(r.readiness()).isEqualTo("DOWN");
        assertThat(r.timestamp()).isEqualTo(now);
    }
}
