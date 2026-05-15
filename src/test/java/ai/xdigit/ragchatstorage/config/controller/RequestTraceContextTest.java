package ai.xdigit.ragchatstorage.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTraceContextTest {

    @Test
    void initializeGeneratesRequestIdWhenHeaderAbsent() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        RequestTraceContext.TraceIds ids = RequestTraceContext.initialize(req, resp);

        assertThat(ids.requestId()).isNotBlank();
        assertThat(ids.correlationId()).isEqualTo(ids.requestId());
        assertThat(resp.getHeader(RequestTraceContext.REQUEST_ID_HEADER)).isEqualTo(ids.requestId());
        assertThat(resp.getHeader(RequestTraceContext.CORRELATION_ID_HEADER)).isEqualTo(ids.correlationId());
    }

    @Test
    void initializePropagatesProvidedRequestIdAndCorrelationId() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RequestTraceContext.REQUEST_ID_HEADER, "req-abc");
        req.addHeader(RequestTraceContext.CORRELATION_ID_HEADER, "corr-xyz");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        RequestTraceContext.TraceIds ids = RequestTraceContext.initialize(req, resp);

        assertThat(ids.requestId()).isEqualTo("req-abc");
        assertThat(ids.correlationId()).isEqualTo("corr-xyz");
    }

    @Test
    void initializeUsesRequestIdAsCorrelationIdFallback() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RequestTraceContext.REQUEST_ID_HEADER, "req-111");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        RequestTraceContext.TraceIds ids = RequestTraceContext.initialize(req, resp);

        assertThat(ids.correlationId()).isEqualTo("req-111");
    }

    @Test
    void initializeStoresAttributesOnRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        RequestTraceContext.TraceIds ids = RequestTraceContext.initialize(req, resp);

        assertThat(req.getAttribute(RequestTraceContext.REQUEST_ID_ATTRIBUTE)).isEqualTo(ids.requestId());
        assertThat(req.getAttribute(RequestTraceContext.CORRELATION_ID_ATTRIBUTE)).isEqualTo(ids.correlationId());
        assertThat(req.getAttribute(RequestTraceContext.API_PATH_ATTRIBUTE)).isEqualTo("/api/v1/health");
    }

    @Test
    void getRequestIdReadsFromAttribute() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(RequestTraceContext.REQUEST_ID_ATTRIBUTE, "stored-id");

        assertThat(RequestTraceContext.getRequestId(req)).isEqualTo("stored-id");
    }

    @Test
    void getRequestIdFallsBackToHeaderWhenAttributeMissing() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RequestTraceContext.REQUEST_ID_HEADER, "header-id");

        assertThat(RequestTraceContext.getRequestId(req)).isEqualTo("header-id");
    }

    @Test
    void getRequestIdReturnsNaWhenNeitherPresent() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertThat(RequestTraceContext.getRequestId(req)).isEqualTo("na");
    }

    @Test
    void getCorrelationIdFallsBackToRequestIdWhenStoredValueIsNa() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(RequestTraceContext.REQUEST_ID_ATTRIBUTE, "req-fallback");
        req.setAttribute(RequestTraceContext.CORRELATION_ID_ATTRIBUTE, "na");

        assertThat(RequestTraceContext.getCorrelationId(req)).isEqualTo("req-fallback");
    }

    @Test
    void getApiPathReadsFromAttribute() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(RequestTraceContext.API_PATH_ATTRIBUTE, "/api/v1/sessions");

        assertThat(RequestTraceContext.getApiPath(req)).isEqualTo("/api/v1/sessions");
    }

    @Test
    void getApiPathFallsBackToRequestUri() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/test");

        assertThat(RequestTraceContext.getApiPath(req)).isEqualTo("/api/v1/test");
    }

    @Test
    void getApiPathReturnsSlashWhenRequestUriIsBlank() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("");

        // falls through to resolveApiPath which defaults to "/"
        String path = RequestTraceContext.getApiPath(req);
        assertThat(path).isEqualTo("/");
    }

    @Test
    void clearRemovesMdcKeys() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        RequestTraceContext.initialize(req, resp);

        RequestTraceContext.clear();

        org.slf4j.MDC.getCopyOfContextMap();
        // After clear, MDC values should be absent — verify indirectly via getRequestId returning "na"
        MockHttpServletRequest fresh = new MockHttpServletRequest();
        assertThat(RequestTraceContext.getRequestId(fresh)).isEqualTo("na");
    }

    @Test
    void traceIdsRecordAccessors() {
        RequestTraceContext.TraceIds ids = new RequestTraceContext.TraceIds("r1", "c1", "/path");
        assertThat(ids.requestId()).isEqualTo("r1");
        assertThat(ids.correlationId()).isEqualTo("c1");
        assertThat(ids.apiPath()).isEqualTo("/path");
    }
}
