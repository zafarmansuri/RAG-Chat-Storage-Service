package ai.xdigit.ragchatstorage.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTest {

    @Test
    void defaultsAreApplied() {
        RateLimitProperties props = new RateLimitProperties();
        assertThat(props.getCapacity()).isEqualTo(60);
        assertThat(props.getWindow()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void settersUpdateValues() {
        RateLimitProperties props = new RateLimitProperties();
        props.setCapacity(120);
        props.setWindow(Duration.ofMinutes(5));

        assertThat(props.getCapacity()).isEqualTo(120);
        assertThat(props.getWindow()).isEqualTo(Duration.ofMinutes(5));
    }
}
