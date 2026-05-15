package ai.xdigit.ragchatstorage.service;

import ai.xdigit.ragchatstorage.dto.CreateSessionRequest;
import ai.xdigit.ragchatstorage.dto.PageResponse;
import ai.xdigit.ragchatstorage.dto.SessionResponse;
import ai.xdigit.ragchatstorage.dto.UpdateSessionRequest;
import ai.xdigit.ragchatstorage.exception.BadRequestException;
import ai.xdigit.ragchatstorage.exception.ResourceNotFoundException;
import ai.xdigit.ragchatstorage.model.ChatSession;
import ai.xdigit.ragchatstorage.repository.ChatMessageRepository;
import ai.xdigit.ragchatstorage.repository.ChatSessionRepository;
import ai.xdigit.ragchatstorage.repository.MessageCountView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business-logic layer for chat session lifecycle operations.
 *
 * <p>All mutating operations enforce ownership by resolving sessions through
 * {@link #getOwnedSession}, which queries by both session ID and {@code userId}.
 * Both "not found" and "wrong owner" cases surface as HTTP 404 to prevent
 * enumeration of sessions belonging to other users.
 *
 * <p>Message counts are denormalised into {@link SessionResponse} via a single
 * batch JPQL query ({@link ChatMessageRepository#countBySessionIds}), avoiding
 * N+1 selects when rendering a full page of sessions.
 *
 * <p>Sessions are sorted by {@code updatedAt DESC, createdAt DESC} so the most
 * recently active conversation always appears first.
 *
 * @see ai.xdigit.ragchatstorage.controller.SessionController
 * @see ChatSessionRepository
 * @see ChatMessageRepository
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String DEFAULT_SESSION_TITLE = "Untitled Chat";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * Creates and persists a new chat session.
     *
     * <p>If {@code request.title()} is null or blank, the title defaults to
     * {@code "Untitled Chat"}. The {@code userId} is trimmed; blank values are
     * rejected with HTTP 400.
     *
     * @param request the creation request carrying an optional title and required userId
     * @return the persisted session as a response DTO with {@code messageCount = 0}
     * @throws BadRequestException if {@code userId} is blank
     */
    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        String normalizedUserId = normalizeUserId(request.userId());
        String title = normalizeTitle(request.title(), true);

        ChatSession session = new ChatSession(UUID.randomUUID(), normalizedUserId, title, false);
        ChatSession saved = chatSessionRepository.save(session);
        return toResponse(saved, 0L);
    }

    /**
     * Returns a paginated, ownership-scoped list of sessions for a user.
     *
     * <p>When {@code favorite} is {@code null} all sessions are returned. When set
     * to {@code true} or {@code false} only sessions matching that flag are included.
     *
     * @param userId   the owner; blank values are rejected with HTTP 400
     * @param favorite optional filter — {@code null} means no filter
     * @param page     zero-based page index
     * @param size     page size (1–100)
     * @return a {@link PageResponse} wrapping {@link SessionResponse} DTOs with
     *         denormalised message counts
     * @throws BadRequestException if {@code userId} is blank
     */
    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> listSessions(String userId, Boolean favorite, int page, int size) {
        String normalizedUserId = normalizeUserId(userId);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt"));

        Page<ChatSession> sessions = favorite == null
                ? chatSessionRepository.findByUserId(normalizedUserId, pageRequest)
                : chatSessionRepository.findByUserIdAndFavorite(normalizedUserId, favorite, pageRequest);

        Map<UUID, Long> messageCounts = resolveMessageCounts(sessions.getContent());
        Page<SessionResponse> responses = sessions.map(session ->
                toResponse(session, messageCounts.getOrDefault(session.getId(), 0L))
        );

        return PageResponse.from(responses);
    }

    /**
     * Patches the title and/or favorite flag of an existing session.
     *
     * <p>At least one field in {@code request} must be non-null; an empty body is
     * rejected with HTTP 400. Only the supplied fields are updated — omitting a
     * field leaves it unchanged.
     *
     * @param sessionId the ID of the session to update
     * @param userId    the caller's user ID; must match the session owner
     * @param request   the patch request; must contain at least one non-null field
     * @return the updated session as a response DTO with a current message count
     * @throws BadRequestException       if the request is empty or title is blank
     * @throws ResourceNotFoundException if the session does not exist or belongs to another user
     */
    @Transactional
    public SessionResponse updateSession(UUID sessionId, String userId, UpdateSessionRequest request) {
        String normalizedUserId = normalizeUserId(userId);
        if (request.isEmpty()) {
            throw new BadRequestException("At least one of title or favorite must be provided");
        }

        ChatSession session = getOwnedSession(sessionId, normalizedUserId);
        if (request.title() != null) {
            session.rename(normalizeTitle(request.title(), false));
        }
        if (request.favorite() != null) {
            session.setFavorite(request.favorite());
        }

        ChatSession saved = chatSessionRepository.save(session);
        long messageCount = chatMessageRepository.countBySession_Id(saved.getId());
        return toResponse(saved, messageCount);
    }

    /**
     * Deletes a session and all of its messages via JPA cascade.
     *
     * <p>Ownership is verified before deletion; wrong-owner and not-found both
     * surface as HTTP 404 to prevent session enumeration.
     *
     * @param sessionId the ID of the session to delete
     * @param userId    the caller's user ID; must match the session owner
     * @throws BadRequestException       if {@code userId} is blank
     * @throws ResourceNotFoundException if the session does not exist or belongs to another user
     */
    @Transactional
    public void deleteSession(UUID sessionId, String userId) {
        ChatSession session = getOwnedSession(sessionId, normalizeUserId(userId));
        chatSessionRepository.delete(session);
    }

    /**
     * Resolves a session by ID and verifies ownership.
     *
     * <p>Used internally by update/delete and by {@link MessageService} when appending
     * or listing messages. Both "not found" and "wrong owner" throw
     * {@link ResourceNotFoundException} so callers cannot enumerate sessions that
     * belong to other users.
     *
     * @param sessionId the session UUID to look up
     * @param userId    the expected owner (already trimmed)
     * @return the matched {@link ChatSession} entity
     * @throws ResourceNotFoundException if the session does not exist or is owned by a different user
     */
    @Transactional(readOnly = true)
    public ChatSession getOwnedSession(UUID sessionId, String userId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session %s was not found for user %s".formatted(sessionId, userId)
                ));
    }

    /**
     * Maps a {@link ChatSession} entity to a {@link SessionResponse} DTO.
     *
     * @param session      the source entity
     * @param messageCount the denormalised count of messages in this session
     * @return the response DTO
     */
    public SessionResponse toResponse(ChatSession session, long messageCount) {
        return new SessionResponse(
                session.getId(),
                session.getUserId(),
                session.getTitle(),
                session.isFavorite(),
                messageCount,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    /**
     * Fetches message counts for all sessions on the current page in one batch query
     * and returns a map keyed by session ID.
     *
     * <p>Sessions with zero messages are absent from the result; callers should use
     * {@link Map#getOrDefault} with a zero default.
     */
    private Map<UUID, Long> resolveMessageCounts(List<ChatSession> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }

        List<UUID> sessionIds = sessions.stream().map(ChatSession::getId).toList();
        return chatMessageRepository.countBySessionIds(sessionIds)
                .stream()
                .collect(Collectors.toMap(MessageCountView::getSessionId, MessageCountView::getMessageCount));
    }

    private String normalizeUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BadRequestException("userId is required");
        }
        return userId.trim();
    }

    private String normalizeTitle(String title, boolean applyDefault) {
        if (!StringUtils.hasText(title)) {
            if (applyDefault) {
                return DEFAULT_SESSION_TITLE;
            }
            throw new BadRequestException("title must not be blank when provided");
        }
        return title.trim();
    }
}
