package ai.xdigit.ragchatstorage.dto;

import ai.xdigit.ragchatstorage.model.Sender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/sessions/{sessionId}/messages}.
 *
 * <p>{@code sender} and {@code content} are mandatory. {@code retrievedContext} is
 * optional and is intended for {@link Sender#ASSISTANT} messages that were generated
 * with a RAG retrieval step. Whitespace-only values for both {@code content} and
 * {@code retrievedContext} are normalised to {@code null}/{@code blank-error} by the
 * service layer before persistence.
 *
 * @param sender           originator of the message; {@code USER} or {@code ASSISTANT}
 * @param content          message body; must not be blank and at most 10 000 characters
 * @param retrievedContext optional RAG source chunks; at most 20 000 characters
 * @see ai.xdigit.ragchatstorage.service.MessageService#addMessage
 */
public record CreateMessageRequest(
        @NotNull(message = "sender is required")
        Sender sender,

        @NotBlank(message = "content is required")
        @Size(max = 10000, message = "content must be at most 10000 characters")
        String content,

        @Size(max = 20000, message = "retrievedContext must be at most 20000 characters")
        String retrievedContext
) {
}
