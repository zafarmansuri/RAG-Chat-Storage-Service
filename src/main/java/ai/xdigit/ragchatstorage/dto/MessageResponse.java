package ai.xdigit.ragchatstorage.dto;

import ai.xdigit.ragchatstorage.model.Sender;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of a {@link ai.xdigit.ragchatstorage.model.ChatMessage}
 * returned from the Messages API.
 *
 * <p>{@code retrievedContext} will be {@code null} for messages that were not
 * produced with a RAG retrieval step (typically all {@link Sender#USER} messages).
 *
 * @param id               unique message identifier
 * @param sessionId        identifier of the owning session
 * @param sender           originator of the message ({@code USER} or {@code ASSISTANT})
 * @param content          message body text
 * @param retrievedContext raw RAG source chunks used during generation, or {@code null}
 * @param createdAt        UTC instant when the message was persisted
 */
public record MessageResponse(
        UUID id,
        UUID sessionId,
        Sender sender,
        String content,
        String retrievedContext,
        Instant createdAt
) {
}
