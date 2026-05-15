package ai.xdigit.ragchatstorage.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Servlet filter that initialises per-request tracing context and logs a
 * structured completion entry for every HTTP request.
 *
 * <p>This filter is registered at {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}
 * by {@link LoggingConfig} so it executes before every other filter, including
 * Spring Security. This guarantees that MDC keys ({@code requestId},
 * {@code correlationId}, {@code apiPath}) are populated for all downstream log
 * statements regardless of whether the request is ultimately rejected by security
 * or rate limiting.
 *
 * <p>On entry, {@link RequestTraceContext#initialize} is called to:
 * <ol>
 *   <li>Generate or propagate {@code X-Request-Id} and {@code X-Correlation-Id}.</li>
 *   <li>Store the IDs as request attributes and MDC keys.</li>
 *   <li>Echo the IDs back in the response headers.</li>
 * </ol>
 *
 * <p>In the {@code finally} block, a {@code request.completed} log line is emitted
 * at a level determined by the HTTP response status:
 * <ul>
 *   <li>5xx — ERROR</li>
 *   <li>4xx — WARN</li>
 *   <li>2xx / 3xx — INFO</li>
 * </ul>
 * MDC is cleared after the log entry so keys do not bleed into subsequent requests
 * on the same thread.
 *
 * @see RequestTraceContext
 * @see LoggingConfig
 */
@Slf4j
public class RequestTracingFilter extends OncePerRequestFilter {

    /**
     * Initialises tracing context, delegates to the next filter, then logs
     * the completed request with method, path, status code, and duration.
     *
     * @param request     the current HTTP request
     * @param response    the current HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        RequestTraceContext.TraceIds traceIds = RequestTraceContext.initialize(request, response);
        long startedAt = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            logCompletion(request, response.getStatus(), traceIds, durationMs);
            RequestTraceContext.clear();
        }
    }

    /**
     * Emits a structured {@code request.completed} log line at the appropriate level.
     *
     * <p>5xx responses are logged at ERROR, 4xx at WARN, and everything else at INFO,
     * so log-level filters and alerting rules can target the right severity without
     * parsing the status code from the message body.
     */
    private void logCompletion(HttpServletRequest request, int status, RequestTraceContext.TraceIds traceIds, long durationMs) {
        String message = "request.completed requestId={} correlationId={} method={} apiPath={} status={} durationMs={}";

        if (status >= 500) {
            log.error(message, traceIds.requestId(), traceIds.correlationId(), request.getMethod(), traceIds.apiPath(), status, durationMs);
            return;
        }

        if (status >= 400) {
            log.warn(message, traceIds.requestId(), traceIds.correlationId(), request.getMethod(), traceIds.apiPath(), status, durationMs);
            return;
        }

        log.info(message, traceIds.requestId(), traceIds.correlationId(), request.getMethod(), traceIds.apiPath(), status, durationMs);
    }
}
