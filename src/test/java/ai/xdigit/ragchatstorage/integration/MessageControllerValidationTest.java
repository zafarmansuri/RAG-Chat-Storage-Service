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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageControllerValidationTest {

    private static final String KEY = "X-API-Key";
    private static final String VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ChatSessionRepository chatSessionRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @BeforeEach
    void clear() {
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
    }

    private UUID createSession() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/sessions")
                        .header(KEY, VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user-1\",\"title\":\"Test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(r.getResponse().getContentAsString());
        return UUID.fromString(node.get("id").asText());
    }

    @Test
    void addMessageWithBlankContentReturns400() throws Exception {
        UUID sessionId = createSession();
        mockMvc.perform(post("/api/v1/sessions/{id}/messages", sessionId)
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":\"USER\",\"content\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMessageWithMissingSenderReturns400() throws Exception {
        UUID sessionId = createSession();
        mockMvc.perform(post("/api/v1/sessions/{id}/messages", sessionId)
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMessageWithInvalidSenderEnumReturns400() throws Exception {
        UUID sessionId = createSession();
        mockMvc.perform(post("/api/v1/sessions/{id}/messages", sessionId)
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":\"ROBOT\",\"content\":\"Hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void addMessageToNonExistentSessionReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{id}/messages", UUID.randomUUID())
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":\"USER\",\"content\":\"Hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void addMessageToSessionOwnedByOtherUserReturns404() throws Exception {
        UUID sessionId = createSession();
        mockMvc.perform(post("/api/v1/sessions/{id}/messages", sessionId)
                        .header(KEY, VALUE)
                        .queryParam("userId", "other-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":\"USER\",\"content\":\"Hello\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addMessageWithMissingUserIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{id}/messages", UUID.randomUUID())
                        .header(KEY, VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":\"USER\",\"content\":\"Hello\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listMessagesForNonExistentSessionReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/{id}/messages", UUID.randomUUID())
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listMessagesWithSizeOver100Returns400() throws Exception {
        UUID sessionId = createSession();
        mockMvc.perform(get("/api/v1/sessions/{id}/messages", sessionId)
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listMessagesWithInvalidSessionUuidReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/not-a-uuid/messages")
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void addMessageWithContentExactly10000CharsSucceeds() throws Exception {
        UUID sessionId = createSession();
        String content = "A".repeat(10000);
        mockMvc.perform(post("/api/v1/sessions/{id}/messages", sessionId)
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":\"USER\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void addMessageWithContextOver20000CharsReturns400() throws Exception {
        UUID sessionId = createSession();
        String ctx = "C".repeat(20001);
        mockMvc.perform(post("/api/v1/sessions/{id}/messages", sessionId)
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":\"ASSISTANT\",\"content\":\"Hi\",\"retrievedContext\":\"" + ctx + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMessageWithBlankContextStoredAsNull() throws Exception {
        UUID sessionId = createSession();
        mockMvc.perform(post("/api/v1/sessions/{id}/messages", sessionId)
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sender\":\"ASSISTANT\",\"content\":\"Hi\",\"retrievedContext\":\"   \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retrievedContext").doesNotExist());
    }
}
