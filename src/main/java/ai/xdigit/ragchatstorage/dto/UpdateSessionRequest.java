package ai.xdigit.ragchatstorage.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/sessions/{sessionId}}.
 *
 * <p>Both fields are optional, but at least one must be present; sending an empty
 * JSON object {@code {}} is rejected with HTTP 400. Supplying {@code title} as a
 * blank or whitespace-only string is also rejected because blank titles are not
 * permitted on update (unlike creation, which falls back to a default).
 *
 * @param title    new session title; at most 255 characters; must not be blank when provided
 * @param favorite new favourite flag; {@code true} to star the session, {@code false} to unstar
 * @see ai.xdigit.ragchatstorage.service.SessionService#updateSession
 */
public record UpdateSessionRequest(
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,
        Boolean favorite
) {
    /**
     * Returns {@code true} when both {@code title} and {@code favorite} are {@code null},
     * meaning no update field was supplied. The service layer rejects such requests with HTTP 400.
     *
     * @return {@code true} if the request carries no updatable fields
     */
    public boolean isEmpty() {
        return title == null && favorite == null;
    }
}
