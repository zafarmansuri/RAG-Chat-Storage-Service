package ai.xdigit.ragchatstorage.security;

import ai.xdigit.ragchatstorage.config.RequestTraceContext;
import ai.xdigit.ragchatstorage.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Servlet filter that authenticates incoming API requests by validating the value of
 * a configurable HTTP header (default: {@code X-API-Key}).
 *
 * <p>The comparison uses {@link MessageDigest#isEqual} for constant-time equality to
 * mitigate timing-based side-channel attacks. On success the filter installs a
 * {@link UsernamePasswordAuthenticationToken} with the {@code ROLE_API_CLIENT} authority
 * into the {@link SecurityContextHolder} for the duration of the request, then clears
 * it in a {@code finally} block.
 *
 * <p>On failure the filter short-circuits the chain and writes an HTTP 401 JSON
 * response with error code {@code UNAUTHORIZED} directly to the response, so that
 * the error payload is consistent with all other error responses.
 *
 * <p>Static assets, Swagger UI, Actuator health probes, H2 console, CORS preflight
 * requests, and the root UI path are excluded from authentication via
 * {@link #shouldNotFilter}.
 *
 * @see RateLimitFilter
 * @see SecurityConfig
 */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final String apiKeyHeader;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    /**
     * Returns {@code true} for paths that do not require authentication: OPTIONS
     * preflight, static resources, Swagger UI, Actuator endpoints, H2 console,
     * and the root UI path.
     *
     * @param request the current HTTP request
     * @return {@code true} if this filter should not be applied to the request
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || path == null
                || path.isBlank()
                || "/".equals(path)
                || PATH_MATCHER.match("/index.html", path)
                || PATH_MATCHER.match("/*.css", path)
                || PATH_MATCHER.match("/*.js", path)
                || PATH_MATCHER.match("/*.ico", path)
                || PATH_MATCHER.match("/h2-console", path)
                || PATH_MATCHER.match("/h2-console/**", path)
                || PATH_MATCHER.match("/swagger-ui/**", path)
                || PATH_MATCHER.match("/swagger-ui.html", path)
                || PATH_MATCHER.match("/v3/api-docs/**", path)
                || PATH_MATCHER.match("/actuator/health/**", path)
                || PATH_MATCHER.match("/actuator/info/**", path)
                || PATH_MATCHER.match("/error", path);
    }

    /**
     * Validates the API key header. Absent or mismatched keys result in a 401 response
     * being written directly without continuing the filter chain.
     *
     * @param request     the current HTTP request
     * @param response    the current HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException      if an I/O error occurs while writing the 401 response
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String providedApiKey = request.getHeader(apiKeyHeader);
        if (!StringUtils.hasText(providedApiKey) || !matches(providedApiKey.trim())) {
            log.warn(
                    "security.rejected requestId={} correlationId={} method={} apiPath={} status={} reason={}",
                    RequestTraceContext.getRequestId(request),
                    RequestTraceContext.getCorrelationId(request),
                    request.getMethod(),
                    RequestTraceContext.getApiPath(request),
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "missing_or_invalid_api_key"
            );
            writeUnauthorized(response, request.getRequestURI());
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "api-client",
                        providedApiKey,
                        AuthorityUtils.createAuthorityList("ROLE_API_CLIENT")
                );

        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Compares the provided key to the configured key using a constant-time algorithm
     * to prevent timing attacks.
     *
     * @param providedApiKey the key extracted from the request header
     * @return {@code true} if the keys match
     */
    private boolean matches(String providedApiKey) {
        return MessageDigest.isEqual(
                providedApiKey.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Writes a JSON 401 response body directly to the servlet response.
     *
     * @param response the current HTTP response
     * @param path     the request URI, included in the error body for client diagnostics
     * @throws IOException if writing to the response fails
     */
    private void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse error = new ApiErrorResponse(
                "UNAUTHORIZED",
                "Missing or invalid API key",
                Instant.now(),
                path
        );
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
