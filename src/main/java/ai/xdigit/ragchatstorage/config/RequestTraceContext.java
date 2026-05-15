package ai.xdigit.ragchatstorage.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Utility class that manages per-request tracing identifiers across the servlet
 * filter chain.
 *
 * <p>Two identifiers are tracked per request:
 * <ul>
 *   <li><strong>requestId</strong> — a unique ID for this specific HTTP request.
 *       Generated as a random UUID if the caller does not supply an
 *       {@code X-Request-Id} header.</li>
 *   <li><strong>correlationId</strong> — an ID used to correlate a group of related
 *       requests (e.g., all requests triggered by a single user action). Taken from
 *       the {@code X-Correlation-Id} header if present; falls back to the
 *       {@code requestId} so that a single request always has a valid correlation
 *       ID.</li>
 * </ul>
 *
 * <p>Both IDs are:
 * <ol>
 *   <li>Stored as request attributes so downstream code can read them without
 *       re-parsing headers.</li>
 *   <li>Echoed in response headers so clients can correlate their requests with
 *       server-side logs.</li>
 *   <li>Inserted into the SLF4J MDC so every log statement in the request scope
 *       automatically includes the IDs.</li>
 * </ol>
 *
 * <p>MDC must be cleared at the end of every request via {@link #clear()} to prevent
 * key leakage to subsequent requests processed on the same thread pool thread.
 *
 * @see RequestTracingFilter
 * @see LoggingConfig
 */
public final class RequestTraceContext {

    /** Header name used to propagate the request-scoped unique identifier. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Header name used to propagate the cross-request correlation identifier. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** Request attribute key under which the request ID is stored. */
    public static final String REQUEST_ID_ATTRIBUTE = RequestTraceContext.class.getName() + ".requestId";

    /** Request attribute key under which the correlation ID is stored. */
    public static final String CORRELATION_ID_ATTRIBUTE = RequestTraceContext.class.getName() + ".correlationId";

    /** Request attribute key under which the API path is stored. */
    public static final String API_PATH_ATTRIBUTE = RequestTraceContext.class.getName() + ".apiPath";

    /** MDC key for the request ID — appears in every log line as {@code requestId}. */
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    /** MDC key for the correlation ID — appears in every log line as {@code correlationId}. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** MDC key for the API path — appears in every log line as {@code apiPath}. */
    public static final String API_PATH_MDC_KEY = "apiPath";

    private RequestTraceContext() {
    }

    /**
     * Resolves tracing IDs from request headers (or generates them), stores them
     * as request attributes and MDC keys, and echoes them in response headers.
     *
     * <p>This method is called once per request by {@link RequestTracingFilter}
     * before the rest of the filter chain executes.
     *
     * @param request  the incoming HTTP request
     * @param response the outgoing HTTP response
     * @return a {@link TraceIds} record containing all three resolved values
     */
    public static TraceIds initialize(HttpServletRequest request, HttpServletResponse response) {
        String requestId = resolveIdentifier(request.getHeader(REQUEST_ID_HEADER));
        String correlationId = resolveCorrelationId(request.getHeader(CORRELATION_ID_HEADER), requestId);
        String apiPath = resolveApiPath(request);

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        request.setAttribute(API_PATH_ATTRIBUTE, apiPath);

        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(API_PATH_MDC_KEY, apiPath);

        return new TraceIds(requestId, correlationId, apiPath);
    }

    /**
     * Removes all tracing keys from MDC.
     *
     * <p>Must be called in the {@code finally} block of {@link RequestTracingFilter}
     * to prevent MDC values from leaking to the next request processed on the same thread.
     */
    public static void clear() {
        MDC.remove(REQUEST_ID_MDC_KEY);
        MDC.remove(CORRELATION_ID_MDC_KEY);
        MDC.remove(API_PATH_MDC_KEY);
    }

    /**
     * Returns the request ID stored in the request attribute, falling back to the
     * raw header value, and then to {@code "na"} if neither is available.
     *
     * @param request the current HTTP request
     * @return the request ID string; never {@code null}
     */
    public static String getRequestId(HttpServletRequest request) {
        return resolveStoredValue(request, REQUEST_ID_ATTRIBUTE, REQUEST_ID_HEADER);
    }

    /**
     * Returns the correlation ID stored in the request attribute, falling back to
     * the raw header and then to the request ID so there is always a valid value.
     *
     * @param request the current HTTP request
     * @return the correlation ID string; never {@code null}
     */
    public static String getCorrelationId(HttpServletRequest request) {
        String correlationId = resolveStoredValue(request, CORRELATION_ID_ATTRIBUTE, CORRELATION_ID_HEADER);
        return "na".equals(correlationId) ? getRequestId(request) : correlationId;
    }

    /**
     * Returns the API path stored in the request attribute, falling back to the
     * raw request URI.
     *
     * @param request the current HTTP request
     * @return the API path; never {@code null}
     */
    public static String getApiPath(HttpServletRequest request) {
        Object attribute = request.getAttribute(API_PATH_ATTRIBUTE);
        if (attribute instanceof String value && StringUtils.hasText(value)) {
            return value;
        }

        return resolveApiPath(request);
    }

    private static String resolveStoredValue(HttpServletRequest request, String attributeName, String headerName) {
        Object attribute = request.getAttribute(attributeName);
        if (attribute instanceof String value && StringUtils.hasText(value)) {
            return value;
        }

        String header = request.getHeader(headerName);
        return StringUtils.hasText(header) ? header.trim() : "na";
    }

    private static String resolveIdentifier(String value) {
        return StringUtils.hasText(value) ? value.trim() : UUID.randomUUID().toString();
    }

    private static String resolveCorrelationId(String value, String fallbackRequestId) {
        return StringUtils.hasText(value) ? value.trim() : fallbackRequestId;
    }

    private static String resolveApiPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return StringUtils.hasText(requestUri) ? requestUri : "/";
    }

    /**
     * Immutable value object carrying the three tracing identifiers resolved for
     * a single request.
     *
     * @param requestId     unique ID for this HTTP request
     * @param correlationId cross-request correlation identifier
     * @param apiPath       the request URI path
     */
    public record TraceIds(String requestId, String correlationId, String apiPath) {
    }
}
