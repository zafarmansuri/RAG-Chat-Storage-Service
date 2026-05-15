package ai.xdigit.ragchatstorage.dto;

import java.time.Instant;

/**
 * Standardised error response body returned for all non-2xx responses.
 *
 * <p>Every error produced by the application (validation failures, authentication
 * errors, not-found, rate-limit exceeded, unexpected exceptions) is serialised into
 * this shape so that clients have a consistent contract for error handling.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "code":      "RESOURCE_NOT_FOUND",
 *   "message":   "Session abc was not found for user alice",
 *   "timestamp": "2024-01-15T10:30:00Z",
 *   "path":      "/api/v1/sessions/abc"
 * }
 * }</pre>
 *
 * @param code      machine-readable error code (e.g. {@code VALIDATION_ERROR},
 *                  {@code UNAUTHORIZED}, {@code RATE_LIMIT_EXCEEDED})
 * @param message   human-readable description of what went wrong
 * @param timestamp UTC instant when the error was generated
 * @param path      the request URI that triggered the error
 * @see ai.xdigit.ragchatstorage.exception.GlobalExceptionHandler
 */
public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String path
) {
}
