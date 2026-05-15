package ai.xdigit.ragchatstorage.repository;

import java.util.UUID;

/**
 * Spring Data JPA projection interface used by the batch message-count query in
 * {@link ChatMessageRepository#countBySessionIds}.
 *
 * <p>Hibernate maps each row of the JPQL aggregate query to an instance of this
 * interface via dynamic proxying, making the result strongly typed without requiring
 * a dedicated DTO class.
 *
 * @see ChatMessageRepository#countBySessionIds(java.util.Collection)
 */
public interface MessageCountView {

    /**
     * Returns the session UUID for this count row.
     *
     * @return non-null session identifier
     */
    UUID getSessionId();

    /**
     * Returns the number of messages belonging to the session identified by
     * {@link #getSessionId()}.
     *
     * @return message count; always &gt;= 1 (zero-message sessions are excluded from the query)
     */
    long getMessageCount();
}
