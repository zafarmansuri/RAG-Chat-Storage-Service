package ai.xdigit.ragchatstorage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/sessions}.
 *
 * <p>{@code userId} is mandatory and identifies the owner of the new session.
 * {@code title} is optional; when absent, blank, or whitespace-only, the service
 * substitutes the default value {@code "Untitled Chat"}.
 *
 * @param userId the owning user identifier; must not be blank and at most 100 characters
 * @param title  optional session title; at most 255 characters
 * @see ai.xdigit.ragchatstorage.service.SessionService#createSession(CreateSessionRequest)
 */
public record CreateSessionRequest(
        @NotBlank(message = "userId is required")
        @Size(max = 100, message = "userId must be at most 100 characters")
        String userId,

        @Size(max = 255, message = "title must be at most 255 characters")
        String title
) {
}
