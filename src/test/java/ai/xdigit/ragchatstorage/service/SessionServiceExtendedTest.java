package ai.xdigit.ragchatstorage.service;

import ai.xdigit.ragchatstorage.dto.CreateSessionRequest;
import ai.xdigit.ragchatstorage.dto.PageResponse;
import ai.xdigit.ragchatstorage.dto.SessionResponse;
import ai.xdigit.ragchatstorage.dto.UpdateSessionRequest;
import ai.xdigit.ragchatstorage.exception.BadRequestException;
import ai.xdigit.ragchatstorage.exception.ResourceNotFoundException;
import ai.xdigit.ragchatstorage.model.ChatSession;
import ai.xdigit.ragchatstorage.repository.ChatMessageRepository;
import ai.xdigit.ragchatstorage.repository.ChatSessionRepository;
import ai.xdigit.ragchatstorage.repository.MessageCountView;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceExtendedTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @InjectMocks
    private SessionService sessionService;

    private ChatSession savedSession(UUID id, String userId, String title) {
        ChatSession s = new ChatSession(id, userId, title, false);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        return s;
    }

    @Test
    void createSessionTrimsUserId() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            s.setCreatedAt(Instant.now());
            s.setUpdatedAt(Instant.now());
            return s;
        });

        SessionResponse resp = sessionService.createSession(new CreateSessionRequest("  user-1  ", "Title"));
        assertThat(resp.userId()).isEqualTo("user-1");
    }

    @Test
    void createSessionPreservesExplicitTitle() {
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            s.setCreatedAt(Instant.now());
            s.setUpdatedAt(Instant.now());
            return s;
        });

        SessionResponse resp = sessionService.createSession(new CreateSessionRequest("user-1", "My Chat"));
        assertThat(resp.title()).isEqualTo("My Chat");
        assertThat(resp.messageCount()).isZero();
    }

    @Test
    void createSessionRejectsBlankUserId() {
        assertThatThrownBy(() ->
                sessionService.createSession(new CreateSessionRequest("   ", "Title"))
        ).isInstanceOf(BadRequestException.class).hasMessageContaining("userId");
    }

    @Test
    void createSessionRejectsNullUserId() {
        assertThatThrownBy(() ->
                sessionService.createSession(new CreateSessionRequest(null, "Title"))
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    void listSessionsReturnsAllSessionsWhenFavoriteIsNull() {
        UUID id = UUID.randomUUID();
        ChatSession s = savedSession(id, "user-1", "Chat");
        PageRequest pr = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt"));
        when(chatSessionRepository.findByUserId(eq("user-1"), any())).thenReturn(new PageImpl<>(List.of(s), pr, 1));
        when(chatMessageRepository.countBySessionIds(List.of(id))).thenReturn(List.of());

        PageResponse<SessionResponse> resp = sessionService.listSessions("user-1", null, 0, 20);

        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.content().get(0).messageCount()).isZero();
    }

    @Test
    void listSessionsFiltersByFavoriteTrue() {
        UUID id = UUID.randomUUID();
        ChatSession s = savedSession(id, "user-1", "Fave");
        when(chatSessionRepository.findByUserIdAndFavorite(eq("user-1"), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(s)));
        when(chatMessageRepository.countBySessionIds(List.of(id))).thenReturn(List.of());

        PageResponse<SessionResponse> resp = sessionService.listSessions("user-1", true, 0, 20);

        assertThat(resp.content()).hasSize(1);
    }

    @Test
    void listSessionsUsesMessageCountFromBatchQuery() {
        UUID id = UUID.randomUUID();
        ChatSession s = savedSession(id, "user-1", "Chat");
        when(chatSessionRepository.findByUserId(eq("user-1"), any())).thenReturn(new PageImpl<>(List.of(s)));
        MessageCountView countView = new MessageCountView() {
            public UUID getSessionId() { return id; }
            public long getMessageCount() { return 5L; }
        };
        when(chatMessageRepository.countBySessionIds(List.of(id))).thenReturn(List.of(countView));

        PageResponse<SessionResponse> resp = sessionService.listSessions("user-1", null, 0, 20);

        assertThat(resp.content().get(0).messageCount()).isEqualTo(5);
    }

    @Test
    void listSessionsReturnsEmptyPageForUnknownUser() {
        when(chatSessionRepository.findByUserId(eq("nobody"), any())).thenReturn(new PageImpl<>(List.of()));

        PageResponse<SessionResponse> resp = sessionService.listSessions("nobody", null, 0, 20);

        assertThat(resp.content()).isEmpty();
        assertThat(resp.totalElements()).isZero();
    }

    @Test
    void updateSessionRejectsEmptyBody() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() ->
                sessionService.updateSession(id, "user-1", new UpdateSessionRequest(null, null))
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateSessionUpdatesFavoriteOnlyWhenTitleIsNull() {
        UUID id = UUID.randomUUID();
        ChatSession s = savedSession(id, "user-1", "Original");
        when(chatSessionRepository.findByIdAndUserId(id, "user-1")).thenReturn(Optional.of(s));
        when(chatSessionRepository.save(s)).thenReturn(s);
        when(chatMessageRepository.countBySession_Id(id)).thenReturn(0L);

        SessionResponse resp = sessionService.updateSession(id, "user-1", new UpdateSessionRequest(null, true));

        assertThat(resp.title()).isEqualTo("Original");
        assertThat(resp.favorite()).isTrue();
    }

    @Test
    void updateSessionRejectsBlankTitle() {
        UUID id = UUID.randomUUID();
        ChatSession s = savedSession(id, "user-1", "Title");
        when(chatSessionRepository.findByIdAndUserId(id, "user-1")).thenReturn(Optional.of(s));

        assertThatThrownBy(() ->
                sessionService.updateSession(id, "user-1", new UpdateSessionRequest("  ", null))
        ).isInstanceOf(BadRequestException.class).hasMessageContaining("title");
    }

    @Test
    void updateSessionThrowsForWrongOwner() {
        UUID id = UUID.randomUUID();
        when(chatSessionRepository.findByIdAndUserId(id, "wrong-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                sessionService.updateSession(id, "wrong-user", new UpdateSessionRequest("T", null))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteSessionRejectsBlankUserId() {
        assertThatThrownBy(() ->
                sessionService.deleteSession(UUID.randomUUID(), "   ")
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    void getOwnedSessionThrowsForMissingSession() {
        UUID id = UUID.randomUUID();
        when(chatSessionRepository.findByIdAndUserId(id, "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getOwnedSession(id, "user-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void toResponseMapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ChatSession s = new ChatSession(id, "user-1", "Title", true);
        s.setCreatedAt(now);
        s.setUpdatedAt(now);

        SessionResponse resp = sessionService.toResponse(s, 7L);

        assertThat(resp.id()).isEqualTo(id);
        assertThat(resp.userId()).isEqualTo("user-1");
        assertThat(resp.title()).isEqualTo("Title");
        assertThat(resp.favorite()).isTrue();
        assertThat(resp.messageCount()).isEqualTo(7L);
        assertThat(resp.createdAt()).isEqualTo(now);
        assertThat(resp.updatedAt()).isEqualTo(now);
    }
}
