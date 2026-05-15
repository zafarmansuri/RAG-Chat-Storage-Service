package ai.xdigit.ragchatstorage.service;

import ai.xdigit.ragchatstorage.dto.CreateMessageRequest;
import ai.xdigit.ragchatstorage.dto.MessageResponse;
import ai.xdigit.ragchatstorage.exception.BadRequestException;
import ai.xdigit.ragchatstorage.model.ChatMessage;
import ai.xdigit.ragchatstorage.model.ChatSession;
import ai.xdigit.ragchatstorage.model.Sender;
import ai.xdigit.ragchatstorage.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceExtendedTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private SessionService sessionService;
    @InjectMocks
    private MessageService messageService;

    private ChatSession session(UUID id) {
        ChatSession s = new ChatSession(id, "user-1", "Chat", false);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.EPOCH);
        return s;
    }

    @Test
    void addMessageRejectsNullSender() {
        assertThatThrownBy(() ->
                messageService.addMessage(UUID.randomUUID(), "user-1",
                        new CreateMessageRequest(null, "Hello", null))
        ).isInstanceOf(BadRequestException.class).hasMessageContaining("sender");
    }

    @Test
    void addMessageRejectsNullContent() {
        assertThatThrownBy(() ->
                messageService.addMessage(UUID.randomUUID(), "user-1",
                        new CreateMessageRequest(Sender.USER, null, null))
        ).isInstanceOf(BadRequestException.class).hasMessageContaining("content");
    }

    @Test
    void addMessageRejectsBlankUserId() {
        assertThatThrownBy(() ->
                messageService.addMessage(UUID.randomUUID(), "   ",
                        new CreateMessageRequest(Sender.USER, "Hello", null))
        ).isInstanceOf(BadRequestException.class).hasMessageContaining("userId");
    }

    @Test
    void addMessageNormalisesBlankRetrievedContextToNull() {
        UUID sessionId = UUID.randomUUID();
        ChatSession sess = session(sessionId);
        when(sessionService.getOwnedSession(sessionId, "user-1")).thenReturn(sess);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setCreatedAt(Instant.now());
            return m;
        });

        MessageResponse resp = messageService.addMessage(sessionId, "user-1",
                new CreateMessageRequest(Sender.ASSISTANT, "Answer", "   "));

        assertThat(resp.retrievedContext()).isNull();
    }

    @Test
    void addMessagePreservesNonBlankRetrievedContext() {
        UUID sessionId = UUID.randomUUID();
        ChatSession sess = session(sessionId);
        when(sessionService.getOwnedSession(sessionId, "user-1")).thenReturn(sess);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setCreatedAt(Instant.now());
            return m;
        });

        MessageResponse resp = messageService.addMessage(sessionId, "user-1",
                new CreateMessageRequest(Sender.ASSISTANT, "Answer", "{\"source\":\"kb-1\"}"));

        assertThat(resp.retrievedContext()).isEqualTo("{\"source\":\"kb-1\"}");
    }

    @Test
    void addMessageTrimsContent() {
        UUID sessionId = UUID.randomUUID();
        ChatSession sess = session(sessionId);
        when(sessionService.getOwnedSession(sessionId, "user-1")).thenReturn(sess);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setCreatedAt(Instant.now());
            return m;
        });

        MessageResponse resp = messageService.addMessage(sessionId, "user-1",
                new CreateMessageRequest(Sender.USER, "  Hello World  ", null));

        assertThat(resp.content()).isEqualTo("Hello World");
    }

    @Test
    void toResponseMapsAllFields() {
        UUID sessionId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        ChatSession sess = session(sessionId);
        ChatMessage msg = new ChatMessage(msgId, sess, Sender.ASSISTANT, "Answer", "ctx");
        msg.setCreatedAt(Instant.now());

        MessageResponse resp = messageService.toResponse(msg);

        assertThat(resp.id()).isEqualTo(msgId);
        assertThat(resp.sessionId()).isEqualTo(sessionId);
        assertThat(resp.sender()).isEqualTo(Sender.ASSISTANT);
        assertThat(resp.content()).isEqualTo("Answer");
        assertThat(resp.retrievedContext()).isEqualTo("ctx");
    }
}
