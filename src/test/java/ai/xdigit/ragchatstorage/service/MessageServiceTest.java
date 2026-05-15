package ai.xdigit.ragchatstorage.service;

import ai.xdigit.ragchatstorage.dto.CreateMessageRequest;
import ai.xdigit.ragchatstorage.dto.MessageResponse;
import ai.xdigit.ragchatstorage.dto.PageResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private MessageService messageService;

    @Test
    void addMessageAppendsMessageAndTouchesSession() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession(sessionId, "user-1", "Chat", false);
        session.setCreatedAt(Instant.parse("2026-05-14T10:15:30Z"));
        session.setUpdatedAt(Instant.EPOCH);

        when(sessionService.getOwnedSession(sessionId, "user-1")).thenReturn(session);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setCreatedAt(Instant.parse("2026-05-14T10:16:00Z"));
            return message;
        });

        MessageResponse response = messageService.addMessage(
                sessionId,
                "user-1",
                new CreateMessageRequest(Sender.USER, "Hello", "{\"chunk\":1}")
        );

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.sender()).isEqualTo(Sender.USER);
        assertThat(response.content()).isEqualTo("Hello");
        assertThat(session.getUpdatedAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void addMessageRejectsBlankContent() {
        assertThatThrownBy(() ->
                messageService.addMessage(
                        UUID.randomUUID(),
                        "user-1",
                        new CreateMessageRequest(Sender.USER, "   ", null)
                )
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    void listMessagesReturnsPaginatedHistory() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession(sessionId, "user-1", "Chat", false);
        session.setCreatedAt(Instant.parse("2026-05-14T10:15:30Z"));
        session.setUpdatedAt(Instant.parse("2026-05-14T10:15:30Z"));

        ChatMessage first = new ChatMessage(UUID.randomUUID(), session, Sender.USER, "Hello", null);
        first.setCreatedAt(Instant.parse("2026-05-14T10:16:00Z"));
        ChatMessage second = new ChatMessage(UUID.randomUUID(), session, Sender.ASSISTANT, "Hi", null);
        second.setCreatedAt(Instant.parse("2026-05-14T10:16:10Z"));

        PageRequest pageRequest = PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "createdAt", "id"));
        when(sessionService.getOwnedSession(sessionId, "user-1")).thenReturn(session);
        when(chatMessageRepository.findBySession_Id(eq(sessionId), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageRequest, 2));

        PageResponse<MessageResponse> response = messageService.listMessages(sessionId, "user-1", 0, 2);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content()).extracting(MessageResponse::content).containsExactly("Hello", "Hi");
    }
}
