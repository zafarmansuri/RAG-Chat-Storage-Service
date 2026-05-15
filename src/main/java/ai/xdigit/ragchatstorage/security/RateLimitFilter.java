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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Servlet filter that enforces per-API-key rate limits using a fixed sliding window.
 *
 * <p>The filter runs after {@link ApiKeyAuthenticationFilter} in the Spring Security
 * chain and is therefore only reached by requests that carry a valid API key. It
 * delegates the token-consumption decision to {@link RateLimitService} and:
 * <ul>
 *   <li>Attaches {@code X-Rate-Limit-Limit} and {@code X-Rate-Limit-Remaining}
 *       headers to every allowed response so clients can monitor their quota.</li>
 *   <li>Returns HTTP 429 with a {@code Retry-After} header and a JSON
 *       {@link ApiErrorResponse} body when the bucket is exhausted.</li>
 * </ul>
 *
 * <p>Static assets, Swagger UI, Actuator health, H2 console, CORS preflight requests,
 * and the application UI are excluded from rate limiting via {@link #shouldNotFilter}.
 *
 * @see RateLimitService
 * @see ApiKeyAuthenticationFilter
 * @see ai.xdigit.ragchatstorage.config.RateLimitProperties
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final String apiKeyHeader;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    /**
     * Returns {@code true} for paths that should bypass rate limiting: OPTIONS
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
     * Consumes one token from the rate-limit bucket keyed by the API key header value.
     * If the request carries no API key (already rejected upstream) it is passed through.
     * If the bucket is exhausted, a 429 response is written directly and the filter
     * chain is not continued.
     *
     * @param request     the current HTTP request
     * @param response    the current HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException      if an I/O error occurs while writing the 429 response
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = request.getHeader(apiKeyHeader);
        if (!StringUtils.hasText(key)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision = rateLimitService.tryConsume(key.trim());
        response.setHeader("X-Rate-Limit-Limit", String.valueOf(rateLimitService.getCapacity()));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            log.warn(
                    "security.rejected requestId={} correlationId={} method={} apiPath={} status={} reason={} retryAfterSeconds={}",
                    RequestTraceContext.getRequestId(request),
                    RequestTraceContext.getCorrelationId(request),
                    request.getMethod(),
                    RequestTraceContext.getApiPath(request),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "rate_limit_exceeded",
                    decision.retryAfterSeconds()
            );
            ApiErrorResponse error = new ApiErrorResponse(
                    "RATE_LIMIT_EXCEEDED",
                    "Too many requests",
                    Instant.now(),
                    request.getRequestURI()
            );
            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
