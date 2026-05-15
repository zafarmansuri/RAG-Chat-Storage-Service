package ai.xdigit.ragchatstorage.service;

import ai.xdigit.ragchatstorage.dto.HealthResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private HealthService healthService;

    @Test
    void getHealthReturnsUpWhenDatabaseResponds() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);

        HealthResponse response = healthService.getHealth();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.liveness()).isEqualTo("UP");
        assertThat(response.readiness()).isEqualTo("UP");
        assertThat(response.isUp()).isTrue();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void getHealthReturnsDownWhenDatabaseThrows() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        HealthResponse response = healthService.getHealth();

        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.liveness()).isEqualTo("UP");
        assertThat(response.readiness()).isEqualTo("DOWN");
        assertThat(response.isUp()).isFalse();
    }
}
