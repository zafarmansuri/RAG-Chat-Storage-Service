package ai.xdigit.ragchatstorage.service;

import ai.xdigit.ragchatstorage.dto.CreateMessageRequest;
import ai.xdigit.ragchatstorage.dto.MessageResponse;
import ai.xdigit.ragchatstorage.dto.PageResponse;
import ai.xdigit.ragchatstorage.exception.BadRequestException;
import ai.xdigit.ragchatstorage.model.ChatMessage;
import ai.xdigit.ragchatstorage.model.ChatSession;
import ai.xdigit.ragchatstorage.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Business-logic layer for chat message operations.
 *
 * <p>Ownership is enforced indirectly: before appending or listing messages the
 * service delegates to {@link SessionService#getOwnedSession}, which verifies that
 * the session belongs to the requesting user and throws HTTP 404 otherwise.
 *
 * <p>Message content is trimmed and must be non-blank. The optional
 * {@code retrievedContext} field is normalised to {@code null} when it is blank or
 * whitespace-only, so the column stays {@code NULL} in the database for plain user
 * messages.
 *
 * <p>Messages are always retrieved in chronological order ({@code createdAt ASC, id ASC})
 * so clients reconstruct conversation history in the correct sequence.
 *
 * @see ai.xdigit.ragchatstorage.controller.MessageController
 * @see SessionService
 * @see ChatMessageRepository
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final SessionService sessionService;

    /**
     * Appends a new message to an existing session.
     *
     * <p>The parent session is looked up and ownership-verified first; if the
     * session does not exist or belongs to a different user, HTTP 404 is returned.
     * After saving the message, {@link ChatSession#touch()} advances the session's
     * {@code updatedAt} timestamp so the session list re-sorts to the top.
     *
     * @param sessionId the ID of the session to append to
     * @param userId    the caller's user ID; must match the session owner
     * @param request   the message creation request
     * @return the persisted message as a response DTO
     * @throws BadRequestException       if {@code userId} or {@code content} is blank, or {@code sender} is null
     * @throws ai.xdigit.ragchatstorage.exception.ResourceNotFoundException if the session does not exist or is not owned by the user
     */
    @Transactional
    public MessageResponse addMessage(UUID sessionId, String userId, CreateMessageRequest request) {
        if (request.sender() == null) {
            throw new BadRequestException("sender is required");
        }
        String content = normalizeContent(request.content());
        String retrievedContext = normalizeOptionalValue(request.retrievedContext());

        ChatSession session = sessionService.getOwnedSession(sessionId, normalizeUserId(userId));
        ChatMessage message = new ChatMessage(UUID.randomUUID(), session, request.sender(), content, retrievedContext);

        ChatMessage saved = chatMessageRepository.save(message);
        session.touch();

        return toResponse(saved);
    }

    /**
     * Returns a paginated, chronologically ordered message history for a session.
     *
     * <p>Ownership of the parent session is verified before fetching messages.
     * Results are sorted by {@code createdAt ASC, id ASC} to guarantee stable
     * ordering even when two messages share the same timestamp.
     *
     * @param sessionId the session whose messages to retrieve
     * @param userId    the caller's user ID; must match the session owner
     * @param page      zero-based page index
     * @param size      page size (1–100)
     * @return a {@link PageResponse} wrapping {@link MessageResponse} DTOs
     * @throws BadRequestException if {@code userId} is blank
     * @throws ai.xdigit.ragchatstorage.exception.ResourceNotFoundException if the session does not exist or is not owned by the user
     */
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> listMessages(UUID sessionId, String userId, int page, int size) {
        ChatSession session = sessionService.getOwnedSession(sessionId, normalizeUserId(userId));
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt", "id"));

        Page<MessageResponse> responses = chatMessageRepository.findBySession_Id(session.getId(), pageRequest).map(this::toResponse);

        return PageResponse.from(responses);
    }

    /**
     * Maps a {@link ChatMessage} entity to a {@link MessageResponse} DTO.
     *
     * @param message the source entity
     * @return the response DTO
     */
    public MessageResponse toResponse(ChatMessage message) {
        return new MessageResponse(message.getId(), message.getSession().getId(), message.getSender(), message.getContent(), message.getRetrievedContext(), message.getCreatedAt());
    }

    private String normalizeUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BadRequestException("userId is required");
        }
        return userId.trim();
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BadRequestException("content is required");
        }
        return content.trim();
    }

    /**
     * Returns {@code null} when the value is blank or whitespace-only, otherwise
     * returns the trimmed value. Keeps {@code retrievedContext} NULL in the database
     * for messages where no RAG context was injected.
     */
    private String normalizeOptionalValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
