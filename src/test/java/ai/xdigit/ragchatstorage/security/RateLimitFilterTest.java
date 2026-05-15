package ai.xdigit.ragchatstorage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private static final String HEADER = "X-API-Key";
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    private RateLimitService serviceWithCapacity(int capacity) {
        ai.xdigit.ragchatstorage.config.RateLimitProperties props = new ai.xdigit.ragchatstorage.config.RateLimitProperties();
        props.setCapacity(capacity);
        props.setWindow(java.time.Duration.ofMinutes(1));
        return new RateLimitService(props);
    }

    @Test
    void allowedRequestPassesThroughAndSetsHeaders() throws Exception {
        RateLimitService svc = serviceWithCapacity(10);
        RateLimitFilter filter = new RateLimitFilter(HEADER, svc, objectMapper);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        req.addHeader(HEADER, "key-1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getHeader("X-Rate-Limit-Limit")).isEqualTo("10");
        assertThat(resp.getHeader("X-Rate-Limit-Remaining")).isNotNull();
    }

    @Test
    void rejectedRequestReturns429AndDoesNotContinueChain() throws Exception {
        RateLimitService svc = serviceWithCapacity(1);
        RateLimitFilter filter = new RateLimitFilter(HEADER, svc, objectMapper);

        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/v1/health");
        req1.addHeader(HEADER, "key-2");
        filter.doFilterInternal(req1, new MockHttpServletResponse(), mock(FilterChain.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/health");
        req2.addHeader(HEADER, "key-2");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        FilterChain chain2 = mock(FilterChain.class);

        filter.doFilterInternal(req2, resp2, chain2);

        verify(chain2, never()).doFilter(any(), any());
        assertThat(resp2.getStatus()).isEqualTo(429);
        assertThat(resp2.getHeader("Retry-After")).isNotNull();
        assertThat(resp2.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void requestWithNoApiKeyPassesThroughWithoutConsuming() throws Exception {
        RateLimitService svc = serviceWithCapacity(1);
        RateLimitFilter filter = new RateLimitFilter(HEADER, svc, objectMapper);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void shouldNotFilterSkipsOptionsRequests() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(HEADER, serviceWithCapacity(10), objectMapper);
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/v1/sessions");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsRootPath() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(HEADER, serviceWithCapacity(10), objectMapper);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsSwaggerAndActuator() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(HEADER, serviceWithCapacity(10), objectMapper);

        assertThat(filter.shouldNotFilter(req("GET", "/swagger-ui.html"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/swagger-ui/index.html"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/v3/api-docs"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/actuator/info"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/h2-console"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/h2-console/tables"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/error"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/index.html"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/app.css"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/app.js"))).isTrue();
        assertThat(filter.shouldNotFilter(req("GET", "/favicon.ico"))).isTrue();
    }

    @Test
    void shouldNotFilterDoesNotSkipApiPath() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(HEADER, serviceWithCapacity(10), objectMapper);
        assertThat(filter.shouldNotFilter(req("GET", "/api/v1/sessions"))).isFalse();
    }

    private MockHttpServletRequest req(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
