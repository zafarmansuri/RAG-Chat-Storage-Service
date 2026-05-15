package ai.xdigit.ragchatstorage.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RequestTracingFilter} covering all three {@code logCompletion}
 * branches (2xx INFO, 4xx WARN, 5xx ERROR) and the MDC-clear-on-exception path.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestTracingFilterTest {

    private final RequestTracingFilter filter = new RequestTracingFilter();

    // -----------------------------------------------------------------------
    // 2xx — INFO branch
    // -----------------------------------------------------------------------

    @Test
    void successfulRequestLogsAtInfoLevel(CapturedOutput output) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setStatus(200);

        FilterChain chain = (request, response) -> { /* no-op, status stays 200 */ };

        filter.doFilterInternal(req, resp, chain);

        assertThat(output).contains("request.completed");
        assertThat(output).contains("status=200");
        assertThat(output).contains("durationMs=");
        // INFO lines typically don't appear with WARN or ERROR prefix in log output
        assertThat(output).contains("INFO");
    }

    @Test
    void successfulRequestEchoesTraceHeadersInResponse(CapturedOutput output) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        req.addHeader(RequestTraceContext.REQUEST_ID_HEADER, "req-123");
        req.addHeader(RequestTraceContext.CORRELATION_ID_HEADER, "corr-456");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, mock(FilterChain.class));

        assertThat(resp.getHeader(RequestTraceContext.REQUEST_ID_HEADER)).isEqualTo("req-123");
        assertThat(resp.getHeader(RequestTraceContext.CORRELATION_ID_HEADER)).isEqualTo("corr-456");
    }

    // -----------------------------------------------------------------------
    // 4xx — WARN branch
    // -----------------------------------------------------------------------

    @Test
    void clientErrorRequestLogsAtWarnLevel(CapturedOutput output) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/sessions");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) ->
                ((MockHttpServletResponse) response).setStatus(400);

        filter.doFilterInternal(req, resp, chain);

        assertThat(output).contains("request.completed");
        assertThat(output).contains("status=400");
        assertThat(output).contains("WARN");
    }

    @Test
    void status404LogsAtWarnLevel(CapturedOutput output) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sessions/missing");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) ->
                ((MockHttpServletResponse) response).setStatus(404);

        filter.doFilterInternal(req, resp, chain);

        assertThat(output).contains("status=404");
        assertThat(output).contains("WARN");
    }

    @Test
    void status429LogsAtWarnLevel(CapturedOutput output) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) ->
                ((MockHttpServletResponse) response).setStatus(429);

        filter.doFilterInternal(req, resp, chain);

        assertThat(output).contains("status=429");
        assertThat(output).contains("WARN");
    }

    // -----------------------------------------------------------------------
    // 5xx — ERROR branch
    // -----------------------------------------------------------------------

    @Test
    void serverErrorRequestLogsAtErrorLevel(CapturedOutput output) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) ->
                ((MockHttpServletResponse) response).setStatus(500);

        filter.doFilterInternal(req, resp, chain);

        assertThat(output).contains("request.completed");
        assertThat(output).contains("status=500");
        assertThat(output).contains("ERROR");
    }

    @Test
    void status503LogsAtErrorLevel(CapturedOutput output) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) ->
                ((MockHttpServletResponse) response).setStatus(503);

        filter.doFilterInternal(req, resp, chain);

        assertThat(output).contains("status=503");
        assertThat(output).contains("ERROR");
    }

    // -----------------------------------------------------------------------
    // MDC cleared even when filterChain.doFilter throws
    // -----------------------------------------------------------------------

    @Test
    void mdcIsClearedEvenWhenChainThrowsServletException(CapturedOutput output) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> {
            throw new ServletException("downstream error");
        };

        assertThatThrownBy(() -> filter.doFilterInternal(req, resp, chain))
                .isInstanceOf(ServletException.class);

        // MDC cleared: a new request with no attributes should resolve to "na"
        MockHttpServletRequest fresh = new MockHttpServletRequest();
        assertThat(RequestTraceContext.getRequestId(fresh)).isEqualTo("na");
        // logCompletion still runs — verify completion log was emitted
        assertThat(output).contains("request.completed");
    }

    @Test
    void mdcIsClearedEvenWhenChainThrowsIOException(CapturedOutput output) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sessions");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> {
            throw new IOException("socket closed");
        };

        assertThatThrownBy(() -> filter.doFilterInternal(req, resp, chain))
                .isInstanceOf(IOException.class);

        assertThat(output).contains("request.completed");
        MockHttpServletRequest fresh = new MockHttpServletRequest();
        assertThat(RequestTraceContext.getRequestId(fresh)).isEqualTo("na");
    }

    // -----------------------------------------------------------------------
    // Log content includes expected structured fields
    // -----------------------------------------------------------------------

    @Test
    void logLineContainsAllStructuredFields(CapturedOutput output) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/api/v1/sessions/abc");
        req.addHeader(RequestTraceContext.REQUEST_ID_HEADER, "req-del");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, (rq, rs) -> {});

        assertThat(output)
                .contains("requestId=req-del")
                .contains("correlationId=req-del")   // falls back to requestId
                .contains("method=DELETE")
                .contains("apiPath=/api/v1/sessions/abc")
                .contains("durationMs=");
    }

    @Test
    void generatesRequestIdWhenNoneProvided(CapturedOutput output) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, (rq, rs) -> {});

        // A UUID-format requestId should appear in the log
        assertThat(output).containsPattern("requestId=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
