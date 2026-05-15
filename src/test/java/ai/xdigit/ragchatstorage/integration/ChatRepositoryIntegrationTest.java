package ai.xdigit.ragchatstorage.integration;

import ai.xdigit.ragchatstorage.model.ChatMessage;
import ai.xdigit.ragchatstorage.model.ChatSession;
import ai.xdigit.ragchatstorage.model.Sender;
import ai.xdigit.ragchatstorage.repository.ChatMessageRepository;
import ai.xdigit.ragchatstorage.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ChatRepositoryIntegrationTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @BeforeEach
    void clearData() {
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
    }

    @Test
    void deletingSessionCascadesMessages() {
        ChatSession session = chatSessionRepository.saveAndFlush(new ChatSession(UUID.randomUUID(), "user-1", "Cascade", false));
        ChatMessage message = new ChatMessage(UUID.randomUUID(), session, Sender.USER, "hello", null);
        chatMessageRepository.saveAndFlush(message);

        chatSessionRepository.delete(session);
        chatSessionRepository.flush();

        assertThat(chatSessionRepository.count()).isZero();
        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    void messagesAreReturnedInAscendingTimestampOrder() {
        ChatSession session = chatSessionRepository.saveAndFlush(new ChatSession(UUID.randomUUID(), "user-1", "Ordering", false));

        ChatMessage first = new ChatMessage(UUID.randomUUID(), session, Sender.USER, "first", null);
        first.setCreatedAt(Instant.parse("2026-05-14T10:15:30Z"));
        ChatMessage second = new ChatMessage(UUID.randomUUID(), session, Sender.ASSISTANT, "second", null);
        second.setCreatedAt(Instant.parse("2026-05-14T10:16:30Z"));

        chatMessageRepository.saveAndFlush(first);
        chatMessageRepository.saveAndFlush(second);

        var page = chatMessageRepository.findBySession_Id(
                session.getId(),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt", "id"))
        );

        assertThat(page.getContent()).extracting(ChatMessage::getContent).containsExactly("first", "second");
    }
}
