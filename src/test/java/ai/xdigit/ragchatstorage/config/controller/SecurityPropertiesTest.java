package ai.xdigit.ragchatstorage.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesTest {

    @Test
    void defaultsAreApplied() {
        SecurityProperties props = new SecurityProperties();
        assertThat(props.getApiKeyHeader()).isEqualTo("X-API-Key");
        assertThat(props.getApiKey()).isEqualTo("change-me");
    }

    @Test
    void settersUpdateValues() {
        SecurityProperties props = new SecurityProperties();
        props.setApiKeyHeader("Authorization");
        props.setApiKey("secret-key-123");

        assertThat(props.getApiKeyHeader()).isEqualTo("Authorization");
        assertThat(props.getApiKey()).isEqualTo("secret-key-123");
    }
}
