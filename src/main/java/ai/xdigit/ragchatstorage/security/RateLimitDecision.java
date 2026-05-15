package ai.xdigit.ragchatstorage.security;

/**
 * Immutable result of a single rate-limit token consumption attempt.
 *
 * <p>Produced by {@link RateLimitService#tryConsume(String)} and consumed by
 * {@link RateLimitFilter} to decide whether to allow or reject the request and to
 * populate the {@code X-Rate-Limit-*} response headers.
 *
 * @param allowed           {@code true} when the request is within the allowed window capacity
 * @param remaining         number of requests remaining in the current window;
 *                          {@code 0} when the bucket is exhausted
 * @param retryAfterSeconds seconds until the window resets and requests are allowed again;
 *                          {@code 0} when the request was allowed
 * @see RateLimitService
 * @see RateLimitFilter
 */
public record RateLimitDecision(boolean allowed, int remaining, long retryAfterSeconds) {
}
