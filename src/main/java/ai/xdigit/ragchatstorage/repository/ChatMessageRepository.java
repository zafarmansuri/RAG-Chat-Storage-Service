package ai.xdigit.ragchatstorage.repository;

import ai.xdigit.ragchatstorage.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ChatMessage} entities.
 *
 * <p>Ownership is <em>not</em> enforced at this level; callers must first resolve the
 * parent {@link ai.xdigit.ragchatstorage.model.ChatSession} via
 * {@link ChatSessionRepository#findByIdAndUserId} to guarantee the session belongs to
 * the requesting user.
 *
 * <p>Messages are stored in append-only fashion and are never updated after creation.
 *
 * @see ai.xdigit.ragchatstorage.service.MessageService
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Returns a page of messages belonging to the given session, ordered by the
     * supplied {@link Pageable} (typically {@code createdAt ASC, id ASC}).
     *
     * @param sessionId the parent session UUID
     * @param pageable  pagination and sorting parameters
     * @return page of messages (may be empty for a session with no messages)
     */
    Page<ChatMessage> findBySession_Id(UUID sessionId, Pageable pageable);

    /**
     * Counts the total number of messages in a single session.
     * Used when refreshing the {@code messageCount} field after a PATCH operation.
     *
     * @param sessionId the parent session UUID
     * @return total message count; 0 if the session has no messages
     */
    long countBySession_Id(UUID sessionId);

    /**
     * Returns per-session message counts for a batch of session IDs in a single query,
     * avoiding N+1 selects when rendering a list of sessions.
     *
     * <p>Sessions with zero messages are absent from the result; callers should default
     * to {@code 0} for any session ID not present in the returned list.
     *
     * @param sessionIds the collection of session UUIDs to count
     * @return list of {@link MessageCountView} projections, one entry per session that has messages
     */
    @Query("""
            select m.session.id as sessionId, count(m.id) as messageCount
            from ChatMessage m
            where m.session.id in :sessionIds
            group by m.session.id
            """)
    List<MessageCountView> countBySessionIds(Collection<UUID> sessionIds);
}
