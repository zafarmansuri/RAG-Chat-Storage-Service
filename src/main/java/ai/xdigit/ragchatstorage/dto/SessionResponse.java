package ai.xdigit.ragchatstorage.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of a {@link ai.xdigit.ragchatstorage.model.ChatSession}
 * returned from the Sessions API.
 *
 * <p>Includes a denormalised {@code messageCount} so that list views can display
 * conversation counts without a separate request.
 *
 * @param id           unique session identifier
 * @param userId       identifier of the owning user
 * @param title        human-readable session title
 * @param favorite     whether the user has starred this session
 * @param messageCount total number of messages stored in this session
 * @param createdAt    UTC instant when the session was created
 * @param updatedAt    UTC instant when the session (or any of its messages) was last modified
 */
public record SessionResponse(
        UUID id,
        String userId,
        String title,
        boolean favorite,
        long messageCount,
        Instant createdAt,
        Instant updatedAt
) {
}
