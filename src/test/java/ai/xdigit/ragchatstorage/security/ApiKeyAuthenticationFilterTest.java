package ai.xdigit.ragchatstorage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiKeyAuthenticationFilterTest {

    private static final String HEADER = "X-API-Key";
    private static final String VALID_KEY = "my-secret";

    private ApiKeyAuthenticationFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        filter = new ApiKeyAuthenticationFilter(HEADER, VALID_KEY, objectMapper);
    }

    @Test
    void validKeyAllowsRequestThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        req.addHeader(HEADER, VALID_KEY);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void missingApiKeyReturns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sessions");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    void wrongApiKeyReturns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sessions");
        req.addHeader(HEADER, "wrong-key");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void keyWithSurroundingWhitespaceIsAccepted() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        req.addHeader(HEADER, "  " + VALID_KEY + "  ");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void shouldNotFilterSkipsOptionsRequests() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/v1/sessions");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsRootPath() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsIndexHtml() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/index.html");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsCssFiles() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/app.css");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsJsFiles() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/app.js");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsIcoFiles() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/favicon.ico");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsSwaggerUi() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsSwaggerHtml() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/swagger-ui.html");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsOpenApiDocs() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v3/api-docs/swagger-config");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsActuatorHealth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsActuatorInfo() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/info");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsH2Console() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/h2-console");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsH2ConsoleSubPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/h2-console/tables");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterSkipsErrorPath() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/error");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterDoesNotSkipApiPath() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sessions");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }
}
