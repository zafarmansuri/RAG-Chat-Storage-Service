package ai.xdigit.ragchatstorage.service;

import ai.xdigit.ragchatstorage.dto.HealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service that performs a lightweight database connectivity check and reports
 * application health.
 *
 * <p>Health is expressed through three independent signals:
 * <ul>
 *   <li><strong>status</strong> — overall health, {@code "UP"} only when the DB probe passes.</li>
 *   <li><strong>liveness</strong> — always {@code "UP"}; the JVM is running if this code executes.</li>
 *   <li><strong>readiness</strong> — {@code "UP"} when the database is reachable,
 *       {@code "DOWN"} when the probe query fails.</li>
 * </ul>
 *
 * <p>The probe executes {@code SELECT 1} via {@link JdbcTemplate}. On failure
 * the exception is logged as a warning (not an error) because transient database
 * hiccups are expected during rolling restarts and should not flood the error log.
 *
 * @see ai.xdigit.ragchatstorage.controller.HealthController
 * @see HealthResponse
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HealthService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Executes a {@code SELECT 1} probe against the configured datasource and
     * returns the resulting health snapshot.
     *
     * <p>On success all three indicators are {@code "UP"}. On any JDBC exception
     * {@code status} and {@code readiness} are set to {@code "DOWN"} and the
     * exception is logged at WARN level without re-throwing.
     *
     * @return a {@link HealthResponse} reflecting the current application state
     */
    public HealthResponse getHealth() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return new HealthResponse("UP", "UP", "UP", Instant.now());
        } catch (Exception exception) {
            log.warn("Readiness check failed", exception);
            return new HealthResponse("DOWN", "UP", "DOWN", Instant.now());
        }
    }
}
