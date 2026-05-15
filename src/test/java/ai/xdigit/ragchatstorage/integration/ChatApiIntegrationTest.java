package ai.xdigit.ragchatstorage.integration;

import ai.xdigit.ragchatstorage.repository.ChatMessageRepository;
import ai.xdigit.ragchatstorage.repository.ChatSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatApiIntegrationTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @BeforeEach
    void clearData() {
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
    }

    @Test
    void chatLifecycleEndpointsWorkEndToEnd() throws Exception {
        MvcResult createSessionResult = mockMvc.perform(post("/api/v1/sessions")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-123",
                                  "title": "Case Study Session"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Case Study Session"))
                .andReturn();

        UUID sessionId = extractId(createSessionResult);

        mockMvc.perform(get("/api/v1/sessions")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .queryParam("userId", "user-123")
                        .queryParam("favorite", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(sessionId.toString()))
                .andExpect(jsonPath("$.content[0].messageCount").value(0));

        mockMvc.perform(patch("/api/v1/sessions/{sessionId}", sessionId)
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .queryParam("userId", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Renamed Session",
                                  "favorite": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed Session"))
                .andExpect(jsonPath("$.favorite").value(true));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/messages", sessionId)
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .queryParam("userId", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sender": "USER",
                                  "content": "What did the retriever find?",
                                  "retrievedContext": "{\\"source\\":\\"kb-1\\"}"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender").value("USER"));

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/messages", sessionId)
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .queryParam("userId", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sender": "ASSISTANT",
                                  "content": "It found two matching snippets.",
                                  "retrievedContext": "{\\"source\\":\\"kb-1\\",\\"chunks\\":2}"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender").value("ASSISTANT"));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/messages", sessionId)
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .queryParam("userId", "user-123")
                        .queryParam("page", "0")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].sender").value("USER"));

        mockMvc.perform(get("/api/v1/sessions")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .queryParam("userId", "user-123")
                        .queryParam("favorite", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].messageCount").value(2))
                .andExpect(jsonPath("$.content[0].favorite").value(true));

        mockMvc.perform(delete("/api/v1/sessions/{sessionId}", sessionId)
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .queryParam("userId", "user-123"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/messages", sessionId)
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .queryParam("userId", "user-123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(chatSessionRepository.count()).isZero();
        assertThat(chatMessageRepository.count()).isZero();
    }

    private UUID extractId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(root.get("id").asText());
    }
}
