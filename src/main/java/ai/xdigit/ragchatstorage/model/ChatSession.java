package ai.xdigit.ragchatstorage.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a single user chat session.
 *
 * <p>A session groups an ordered sequence of {@link ChatMessage} records under a
 * human-readable title and tracks whether the user has starred it as a favourite.
 * Sessions are owned by a logical {@code userId} string (an opaque identifier
 * supplied by the caller); all repository queries enforce ownership so that one
 * user cannot read or mutate another user's sessions.
 *
 * <p>Timestamps ({@code createdAt}, {@code updatedAt}) are managed automatically
 * via JPA lifecycle callbacks and are always stored in UTC.
 *
 * <p>Deleting a session cascades to all associated {@link ChatMessage} records
 * through {@code CascadeType.ALL} + {@code orphanRemoval = true}.
 *
 * @see ChatMessage
 * @see ai.xdigit.ragchatstorage.repository.ChatSessionRepository
 * @see ai.xdigit.ragchatstorage.service.SessionService
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_sessions")
public class ChatSession {

    /**
     * Stable UUID primary key supplied by the service layer, never database-generated.
     */
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /**
     * Opaque identifier of the user who owns this session.
     * Maximum length 100 characters; whitespace is trimmed before persistence.
     */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /**
     * Human-readable session title. Defaults to {@code "Untitled Chat"} when not
     * supplied on creation. Maximum length 255 characters.
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * Whether the user has starred this session for quick access. Defaults to {@code false}.
     */
    @Column(nullable = false)
    private boolean favorite;

    /**
     * UTC instant at which this session was first persisted. Immutable after creation.
     */
    @Setter
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * UTC instant at which this session was last modified — updated on rename,
     * favourite toggle, or whenever a new message is appended via {@link #touch()}.
     */
    @Setter
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * All messages belonging to this session.
     * Cascade-all and orphan-removal ensure they are deleted together with the session.
     */
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ChatMessage> messages = new ArrayList<>();

    /**
     * Creates a new session with the supplied identity fields.
     *
     * @param id       stable UUID for this session
     * @param userId   owning user identifier; must be non-blank
     * @param title    session title; must be non-blank
     * @param favorite initial favourite flag ({@code false} for new sessions)
     */
    public ChatSession(UUID id, String userId, String title, boolean favorite) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.favorite = favorite;
    }

    /**
     * JPA pre-persist callback. Sets {@code createdAt} and {@code updatedAt} to
     * {@link Instant#now()}, and assigns a random UUID to {@code id} if not already set.
     */
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /**
     * JPA pre-update callback. Advances {@code updatedAt} to {@link Instant#now()}
     * whenever Hibernate flushes a dirty session to the database.
     */
    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Replaces the session title.
     *
     * @param title new title; must be non-blank and at most 255 characters
     */
    public void rename(String title) {
        this.title = title;
    }

    /**
     * Updates the favourite flag.
     *
     * @param favorite {@code true} to star the session, {@code false} to unstar it
     */
    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    /**
     * Manually advances {@code updatedAt} to {@link Instant#now()}.
     * Called after a new message is appended so the session list
     * continues to sort by most-recently-active.
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }
}
