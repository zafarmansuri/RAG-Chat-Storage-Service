package ai.xdigit.ragchatstorage.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.rate-limit.capacity=2",
        "app.rate-limit.window=PT1H"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RateLimitIntegrationTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exceedingConfiguredLimitReturnsTooManyRequests() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/health").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/health").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
