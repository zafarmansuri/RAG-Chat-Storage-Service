package ai.xdigit.ragchatstorage.security;

import ai.xdigit.ragchatstorage.config.RateLimitProperties;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process, fixed-window rate limiter keyed by API key string.
 *
 * <p>Maintains one {@code WindowState} per unique API key in a {@link ConcurrentHashMap}.
 * Each state tracks a counter and the start time of the current window. When a new
 * request arrives:
 * <ol>
 *   <li>If the elapsed time since {@code windowStartedAt} exceeds the configured
 *       {@code window} duration, the counter resets to 0 and the window restarts.</li>
 *   <li>If the counter is below the configured {@code capacity}, the request is
 *       allowed and the counter is incremented.</li>
 *   <li>Otherwise the request is rejected and the filter receives a
 *       {@code retryAfterSeconds} value indicating when the window expires.</li>
 * </ol>
 *
 * <p>The per-key {@code WindowState} uses {@code synchronized} for thread safety.
 * This is sufficient for the expected low cardinality of API keys (one or a handful
 * of callers) and avoids adding an external cache dependency.
 *
 * <p>Default configuration: 60 requests per minute per key (see
 * {@link RateLimitProperties}).
 *
 * @see RateLimitFilter
 * @see RateLimitDecision
 */
@Service
public class RateLimitService {

    @Getter
    private final int capacity;
    private final long windowMillis;
    private final ConcurrentHashMap<String, WindowState> windows = new ConcurrentHashMap<>();

    /**
     * Constructs the service from the application's rate-limit configuration.
     *
     * @param rateLimitProperties bound configuration properties
     */
    public RateLimitService(RateLimitProperties rateLimitProperties) {
        this.capacity = rateLimitProperties.getCapacity();
        this.windowMillis = rateLimitProperties.getWindow().toMillis();
    }

    /**
     * Attempts to consume one token from the bucket associated with {@code key}.
     *
     * @param key the rate-limit key, typically the raw API key string
     * @return a {@link RateLimitDecision} describing whether the request is allowed
     *         and how many tokens remain
     */
    public RateLimitDecision tryConsume(String key) {
        WindowState state = windows.computeIfAbsent(key, ignored -> new WindowState());
        return state.tryConsume(capacity, windowMillis);
    }

    /**
     * Thread-safe sliding-window state for a single rate-limit key.
     * All mutations are guarded by {@code synchronized} on the instance.
     */
    private static final class WindowState {
        private long windowStartedAt = System.currentTimeMillis();
        private int count;

        /**
         * Attempts to consume one token. Resets the window counter if the window
         * has expired since the last call.
         *
         * @param capacity     maximum allowed requests per window
         * @param windowMillis window duration in milliseconds
         * @return the consumption decision
         */
        private synchronized RateLimitDecision tryConsume(int capacity, long windowMillis) {
            long now = System.currentTimeMillis();
            if (now - windowStartedAt >= windowMillis) {
                windowStartedAt = now;
                count = 0;
            }

            if (count < capacity) {
                count++;
                return new RateLimitDecision(true, Math.max(capacity - count, 0), 0);
            }

            long retryAfterMillis = Math.max(windowMillis - (now - windowStartedAt), 0);
            long retryAfterSeconds = Math.max((retryAfterMillis + 999) / 1000, 1);
            return new RateLimitDecision(false, 0, retryAfterSeconds);
        }
    }
}
