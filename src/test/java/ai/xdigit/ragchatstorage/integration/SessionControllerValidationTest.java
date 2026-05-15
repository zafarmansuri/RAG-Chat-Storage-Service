package ai.xdigit.ragchatstorage.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionControllerValidationTest {

    private static final String KEY = "X-API-Key";
    private static final String VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createSessionWithBlankUserIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .header(KEY, VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"title\":\"T\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createSessionWithNullUserIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .header(KEY, VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSessionWithTitleOver255CharsReturns400() throws Exception {
        String longTitle = "A".repeat(256);
        mockMvc.perform(post("/api/v1/sessions")
                        .header(KEY, VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user-1\",\"title\":\"" + longTitle + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSessionWithUserIdOver100CharsReturns400() throws Exception {
        String longId = "u".repeat(101);
        mockMvc.perform(post("/api/v1/sessions")
                        .header(KEY, VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + longId + "\",\"title\":\"T\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSessionWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .header(KEY, VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void listSessionsWithMissingUserIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/sessions").header(KEY, VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSessionsWithNegativePageReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/sessions")
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .queryParam("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSessionsWithSizeOver100Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/sessions")
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSessionsWithSizeZeroReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/sessions")
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .queryParam("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSessionWithInvalidUuidReturns400() throws Exception {
        mockMvc.perform(patch("/api/v1/sessions/not-a-uuid")
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void updateSessionWithEmptyBodyReturns400() throws Exception {
        mockMvc.perform(patch("/api/v1/sessions/" + UUID.randomUUID())
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSessionWithTitleOver255CharsReturns400() throws Exception {
        String longTitle = "A".repeat(256);
        mockMvc.perform(patch("/api/v1/sessions/" + UUID.randomUUID())
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateNonExistentSessionReturns404() throws Exception {
        mockMvc.perform(patch("/api/v1/sessions/" + UUID.randomUUID())
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteNonExistentSessionReturns404() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/" + UUID.randomUUID())
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSessionWithInvalidUuidReturns400() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/not-a-uuid")
                        .header(KEY, VALUE)
                        .queryParam("userId", "user-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRouteReturns404WithJsonBody() throws Exception {
        mockMvc.perform(get("/api/v1/nonexistent")
                        .header(KEY, VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
