package ai.xdigit.ragchatstorage.dto;

import java.time.Instant;

/**
 * Response body for {@code GET /api/v1/health}.
 *
 * <p>Follows Kubernetes liveness/readiness probe conventions:
 * <ul>
 *   <li>{@code liveness} — the process is running ({@code "UP"} always, unless the
 *       JVM is critically broken)</li>
 *   <li>{@code readiness} — the service can handle requests, which requires a
 *       reachable database ({@code "UP"} or {@code "DOWN"})</li>
 *   <li>{@code status} — an aggregate; {@code "UP"} only when both probes are up</li>
 * </ul>
 *
 * @param status    aggregate status: {@code "UP"} or {@code "DOWN"}
 * @param liveness  liveness probe result: {@code "UP"} or {@code "DOWN"}
 * @param readiness readiness probe result: {@code "UP"} or {@code "DOWN"}
 * @param timestamp UTC instant at which the health check was performed
 * @see ai.xdigit.ragchatstorage.service.HealthService
 */
public record HealthResponse(
        String status,
        String liveness,
        String readiness,
        Instant timestamp
) {
    /**
     * Convenience predicate that returns {@code true} when {@code status} equals
     * {@code "UP"} (case-insensitive).
     *
     * @return {@code true} if the service is healthy
     */
    public boolean isUp() {
        return "UP".equalsIgnoreCase(status);
    }
}
