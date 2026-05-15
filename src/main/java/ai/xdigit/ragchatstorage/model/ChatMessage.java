package ai.xdigit.ragchatstorage.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an individual message within a {@link ChatSession}.
 *
 * <p>A message is the fundamental storage unit of the RAG chat history. Each record
 * captures who sent the message ({@link Sender}), the message text, and an optional
 * {@code retrievedContext} field that stores the raw source chunks retrieved by the
 * RAG pipeline before generating the assistant response. This context can be inspected
 * later to audit or replay retrieval decisions.
 *
 * <p>Messages are effectively immutable after creation. The {@code setCreatedAt} setter
 * exists solely to support test fixtures that require deterministic ordering.
 *
 * <p>Ownership is enforced indirectly: the service layer resolves the parent
 * {@link ChatSession} using both {@code sessionId} and {@code userId} before saving a
 * message, preventing a user from appending messages to another user's session.
 *
 * @see ChatSession
 * @see Sender
 * @see ai.xdigit.ragchatstorage.repository.ChatMessageRepository
 * @see ai.xdigit.ragchatstorage.service.MessageService
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_messages")
public class ChatMessage {

    /** Stable UUID primary key supplied by the service layer. */
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /**
     * Parent session. Loaded lazily to avoid unnecessary joins when only
     * message-level data is needed.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    /** Whether the message was produced by the human user or the AI assistant. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Sender sender;

    /**
     * The message body text. Stored as {@code TEXT} to accommodate long assistant
     * responses. Maximum 10 000 characters, enforced at the API boundary.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Raw chunks retrieved from the vector store by the RAG pipeline, concatenated
     * before being passed to the language model. {@code null} when the message was
     * not produced with retrieval (e.g. {@link Sender#USER} messages or assistant
     * replies that did not perform retrieval). Maximum 20 000 characters.
     */
    @Column(name = "retrieved_context", columnDefinition = "TEXT")
    private String retrievedContext;

    /** UTC instant at which this message was persisted. Set once by {@link #prePersist()}. */
    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Creates a new message associated with the given session.
     *
     * @param id               stable UUID for this message
     * @param session          parent session; must not be null
     * @param sender           originator of the message; must not be null
     * @param content          message body; must be non-blank and at most 10 000 characters
     * @param retrievedContext RAG source chunks, or {@code null} if not applicable
     */
    public ChatMessage(UUID id, ChatSession session, Sender sender, String content, String retrievedContext) {
        this.id = id;
        this.session = session;
        this.sender = sender;
        this.content = content;
        this.retrievedContext = retrievedContext;
    }

    /**
     * JPA pre-persist callback. Assigns a random UUID to {@code id} if not set,
     * and stamps {@code createdAt} with {@link Instant#now()}.
     */
    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
