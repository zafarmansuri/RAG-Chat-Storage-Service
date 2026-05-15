package ai.xdigit.ragchatstorage.service;

import ai.xdigit.ragchatstorage.dto.CreateSessionRequest;
import ai.xdigit.ragchatstorage.dto.SessionResponse;
import ai.xdigit.ragchatstorage.dto.UpdateSessionRequest;
import ai.xdigit.ragchatstorage.exception.ResourceNotFoundException;
import ai.xdigit.ragchatstorage.model.ChatSession;
import ai.xdigit.ragchatstorage.repository.ChatMessageRepository;
import ai.xdigit.ragchatstorage.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private SessionService sessionService;

    @Test
    void createSessionUsesDefaultTitleWhenMissing() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setCreatedAt(Instant.parse("2026-05-14T10:15:30Z"));
            session.setUpdatedAt(Instant.parse("2026-05-14T10:15:30Z"));
            return session;
        });

        SessionResponse response = sessionService.createSession(new CreateSessionRequest("user-1", " "));

        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.title()).isEqualTo("Untitled Chat");
        assertThat(response.messageCount()).isZero();
    }

    @Test
    void updateSessionAppliesTitleAndFavoriteChanges() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession(sessionId, "user-1", "Original", false);
        session.setCreatedAt(Instant.parse("2026-05-14T10:15:30Z"));
        session.setUpdatedAt(Instant.parse("2026-05-14T10:15:30Z"));

        when(chatSessionRepository.findByIdAndUserId(sessionId, "user-1")).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(session)).thenReturn(session);
        when(chatMessageRepository.countBySession_Id(sessionId)).thenReturn(3L);

        SessionResponse response = sessionService.updateSession(
                sessionId,
                "user-1",
                new UpdateSessionRequest("Renamed", true)
        );

        assertThat(response.title()).isEqualTo("Renamed");
        assertThat(response.favorite()).isTrue();
        assertThat(response.messageCount()).isEqualTo(3);
    }

    @Test
    void deleteSessionDeletesOwnedSession() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession(sessionId, "user-1", "Title", false);

        when(chatSessionRepository.findByIdAndUserId(sessionId, "user-1")).thenReturn(Optional.of(session));

        sessionService.deleteSession(sessionId, "user-1");

        verify(chatSessionRepository).delete(session);
    }

    @Test
    void updateSessionThrowsWhenSessionIsMissing() {
        UUID sessionId = UUID.randomUUID();
        when(chatSessionRepository.findByIdAndUserId(sessionId, "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                sessionService.updateSession(sessionId, "user-1", new UpdateSessionRequest("Renamed", null))
        ).isInstanceOf(ResourceNotFoundException.class);
    }
}
