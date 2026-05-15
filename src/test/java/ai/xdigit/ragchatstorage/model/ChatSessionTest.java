package ai.xdigit.ragchatstorage.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionTest {

    @Test
    void constructorSetsAllFields() {
        UUID id = UUID.randomUUID();
        ChatSession session = new ChatSession(id, "user-1", "My Chat", true);

        assertThat(session.getId()).isEqualTo(id);
        assertThat(session.getUserId()).isEqualTo("user-1");
        assertThat(session.getTitle()).isEqualTo("My Chat");
        assertThat(session.isFavorite()).isTrue();
        assertThat(session.getMessages()).isEmpty();
    }

    @Test
    void prePersistSetsTimestampsAndKeepsExistingId() {
        UUID id = UUID.randomUUID();
        ChatSession session = new ChatSession(id, "user-1", "Title", false);
        session.prePersist();

        assertThat(session.getId()).isEqualTo(id);
        assertThat(session.getCreatedAt()).isNotNull();
        assertThat(session.getUpdatedAt()).isNotNull();
    }

    @Test
    void prePersistGeneratesIdWhenNull() {
        ChatSession session = new ChatSession(null, "user-1", "Title", false);
        session.prePersist();

        assertThat(session.getId()).isNotNull();
    }

    @Test
    void prePersistDoesNotOverwriteExistingCreatedAt() {
        ChatSession session = new ChatSession(UUID.randomUUID(), "user-1", "Title", false);
        Instant fixed = Instant.parse("2020-01-01T00:00:00Z");
        session.setCreatedAt(fixed);
        session.prePersist();

        assertThat(session.getCreatedAt()).isEqualTo(fixed);
    }

    @Test
    void preUpdateAdvancesUpdatedAt() throws InterruptedException {
        ChatSession session = new ChatSession(UUID.randomUUID(), "user-1", "Title", false);
        session.prePersist();
        Instant before = session.getUpdatedAt();

        Thread.sleep(2);
        session.preUpdate();

        assertThat(session.getUpdatedAt()).isAfter(before);
    }

    @Test
    void renameChangesTitle() {
        ChatSession session = new ChatSession(UUID.randomUUID(), "user-1", "Old", false);
        session.rename("New Title");

        assertThat(session.getTitle()).isEqualTo("New Title");
    }

    @Test
    void setFavoriteFlipsFlag() {
        ChatSession session = new ChatSession(UUID.randomUUID(), "user-1", "Title", false);
        session.setFavorite(true);
        assertThat(session.isFavorite()).isTrue();

        session.setFavorite(false);
        assertThat(session.isFavorite()).isFalse();
    }

    @Test
    void touchAdvancesUpdatedAt() throws InterruptedException {
        ChatSession session = new ChatSession(UUID.randomUUID(), "user-1", "Title", false);
        Instant fixed = Instant.parse("2020-01-01T00:00:00Z");
        session.setUpdatedAt(fixed);

        Thread.sleep(2);
        session.touch();

        assertThat(session.getUpdatedAt()).isAfter(fixed);
    }

    @Test
    void setCreatedAtAndSetUpdatedAtOverrideTimestamps() {
        ChatSession session = new ChatSession(UUID.randomUUID(), "user-1", "Title", false);
        Instant createdAt = Instant.parse("2021-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2022-01-01T00:00:00Z");

        session.setCreatedAt(createdAt);
        session.setUpdatedAt(updatedAt);

        assertThat(session.getCreatedAt()).isEqualTo(createdAt);
        assertThat(session.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
