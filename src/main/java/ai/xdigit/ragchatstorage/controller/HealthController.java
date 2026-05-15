package ai.xdigit.ragchatstorage.controller;

import ai.xdigit.ragchatstorage.dto.HealthResponse;
import ai.xdigit.ragchatstorage.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes application health information.
 *
 * <p>Mounted at {@code /api/v1/health}, this endpoint complements the Spring
 * Actuator probes ({@code /actuator/health}) with a richer, application-level view
 * that includes liveness, readiness, and overall status in a single response.
 *
 * <p>HTTP status mapping:
 * <ul>
 *   <li>200 OK — all health indicators are {@code "UP"}</li>
 *   <li>503 Service Unavailable — at least one indicator is {@code "DOWN"}
 *       (typically a database connectivity failure)</li>
 * </ul>
 *
 * <p>The endpoint requires a valid {@code X-API-Key} header so that internal health
 * detail is not publicly readable. Kubernetes liveness/readiness probes that do not
 * carry an API key should use the Actuator endpoints instead.
 *
 * @see HealthService
 * @see HealthResponse
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Application readiness and liveness")
@SecurityRequirement(name = "apiKey")
public class HealthController {

    private final HealthService healthService;

    /**
     * Returns the current health of the application.
     *
     * <p>HTTP 200 is returned when all indicators are {@code "UP"}.
     * HTTP 503 is returned when the database probe fails, signalling that the
     * application is not ready to serve traffic.
     *
     * @return a {@link ResponseEntity} containing the {@link HealthResponse};
     *         status is 200 when healthy, 503 when unhealthy
     */
    @GetMapping
    @Operation(summary = "Get service readiness and liveness")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthService.getHealth();
        HttpStatus status = response.isUp() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }
}
