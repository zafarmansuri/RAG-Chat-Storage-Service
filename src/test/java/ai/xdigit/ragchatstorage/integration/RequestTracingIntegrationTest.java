package ai.xdigit.ragchatstorage.integration;

import ai.xdigit.ragchatstorage.config.RequestTraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RequestTracingIntegrationTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatedTraceHeadersAreReturnedAndSuccessIsLogged(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/health").header(API_KEY_HEADER, API_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestTraceContext.REQUEST_ID_HEADER))
                .andExpect(header().exists(RequestTraceContext.CORRELATION_ID_HEADER))
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestTraceContext.REQUEST_ID_HEADER);
        String correlationId = result.getResponse().getHeader(RequestTraceContext.CORRELATION_ID_HEADER);

        assertThat(requestId).isNotBlank();
        assertThat(correlationId).isNotBlank();
        assertThat(output)
                .contains("request.completed")
                .contains("requestId=" + requestId)
                .contains("correlationId=" + correlationId)
                .contains("apiPath=/api/v1/health")
                .contains("status=200")
                .contains("durationMs=");
    }

    @Test
    void handledValidationFailuresReuseProvidedTraceHeadersAndLogFailure(CapturedOutput output) throws Exception {
        String requestId = "req-validation-001";
        String correlationId = "corr-validation-001";

        mockMvc.perform(post("/api/v1/sessions")
                        .header(API_KEY_HEADER, API_KEY_VALUE)
                        .header(RequestTraceContext.REQUEST_ID_HEADER, requestId)
                        .header(RequestTraceContext.CORRELATION_ID_HEADER, correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "",
                                  "title": "Broken Request"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestTraceContext.REQUEST_ID_HEADER, requestId))
                .andExpect(header().string(RequestTraceContext.CORRELATION_ID_HEADER, correlationId))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(output)
                .contains("request.failed")
                .contains("requestId=" + requestId)
                .contains("correlationId=" + correlationId)
                .contains("apiPath=/api/v1/sessions")
                .contains("status=400")
                .contains("code=VALIDATION_ERROR");
    }
}
