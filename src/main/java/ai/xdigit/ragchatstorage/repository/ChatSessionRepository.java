package ai.xdigit.ragchatstorage.repository;

import ai.xdigit.ragchatstorage.model.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ChatSession} entities.
 *
 * <p>All finder methods scope results to a single {@code userId} to enforce
 * data ownership — one user's sessions are never visible to another user.
 * Results are typically sorted by {@code updatedAt DESC, createdAt DESC}
 * (applied by the service layer via {@link Pageable}).
 *
 * @see ai.xdigit.ragchatstorage.service.SessionService
 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    /**
     * Returns a page of all sessions owned by the given user, sorted by the
     * supplied {@link Pageable} specification.
     *
     * @param userId   the owning user identifier
     * @param pageable pagination and sorting parameters
     * @return page of matching sessions (may be empty)
     */
    Page<ChatSession> findByUserId(String userId, Pageable pageable);

    /**
     * Returns a page of sessions owned by the given user filtered by favourite status.
     *
     * @param userId   the owning user identifier
     * @param favorite {@code true} to return starred sessions, {@code false} for non-starred
     * @param pageable pagination and sorting parameters
     * @return page of matching sessions (may be empty)
     */
    Page<ChatSession> findByUserIdAndFavorite(String userId, boolean favorite, Pageable pageable);

    /**
     * Looks up a single session by its primary key <em>and</em> owner, returning an empty
     * {@link Optional} when either the ID does not exist or the session belongs to a
     * different user. This is the primary ownership-enforcement query.
     *
     * @param id     session UUID
     * @param userId the expected owning user identifier
     * @return the session if it exists and is owned by {@code userId}; empty otherwise
     */
    Optional<ChatSession> findByIdAndUserId(UUID id, String userId);
}
