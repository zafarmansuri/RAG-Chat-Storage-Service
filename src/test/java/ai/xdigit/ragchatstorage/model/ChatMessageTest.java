package ai.xdigit.ragchatstorage.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageTest {

    private ChatSession anySession() {
        ChatSession s = new ChatSession(UUID.randomUUID(), "user-1", "Title", false);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        return s;
    }

    @Test
    void constructorSetsAllFields() {
        UUID id = UUID.randomUUID();
        ChatSession session = anySession();
        ChatMessage msg = new ChatMessage(id, session, Sender.USER, "Hello", "ctx");

        assertThat(msg.getId()).isEqualTo(id);
        assertThat(msg.getSession()).isSameAs(session);
        assertThat(msg.getSender()).isEqualTo(Sender.USER);
        assertThat(msg.getContent()).isEqualTo("Hello");
        assertThat(msg.getRetrievedContext()).isEqualTo("ctx");
    }

    @Test
    void constructorWithNullRetrievedContextIsAllowed() {
        ChatMessage msg = new ChatMessage(UUID.randomUUID(), anySession(), Sender.ASSISTANT, "Answer", null);
        assertThat(msg.getRetrievedContext()).isNull();
    }

    @Test
    void prePersistSetsCreatedAtWhenNull() {
        ChatMessage msg = new ChatMessage(UUID.randomUUID(), anySession(), Sender.USER, "Hi", null);
        msg.prePersist();

        assertThat(msg.getCreatedAt()).isNotNull();
    }

    @Test
    void prePersistDoesNotOverwriteExistingCreatedAt() {
        ChatMessage msg = new ChatMessage(UUID.randomUUID(), anySession(), Sender.USER, "Hi", null);
        Instant fixed = Instant.parse("2020-06-01T00:00:00Z");
        msg.setCreatedAt(fixed);
        msg.prePersist();

        assertThat(msg.getCreatedAt()).isEqualTo(fixed);
    }

    @Test
    void prePersistGeneratesIdWhenNull() {
        ChatMessage msg = new ChatMessage(null, anySession(), Sender.USER, "Hi", null);
        msg.prePersist();

        assertThat(msg.getId()).isNotNull();
    }

    @Test
    void setCreatedAtOverridesTimestamp() {
        ChatMessage msg = new ChatMessage(UUID.randomUUID(), anySession(), Sender.USER, "Hi", null);
        Instant ts = Instant.parse("2023-03-01T12:00:00Z");
        msg.setCreatedAt(ts);

        assertThat(msg.getCreatedAt()).isEqualTo(ts);
    }
}
