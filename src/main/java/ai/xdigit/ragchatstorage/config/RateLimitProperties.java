package ai.xdigit.ragchatstorage.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Bound configuration properties for the per-API-key rate limiter.
 *
 * <p>Properties are read from the {@code app.rate-limit} prefix in
 * {@code application.properties} / {@code application.yml} or the corresponding
 * environment variables (e.g., {@code APP_RATE_LIMIT_CAPACITY}).
 *
 * <p>Both fields are validated on startup; the application will not start if
 * {@code capacity} is not positive or {@code window} is null.
 *
 * <p>Default values:
 * <ul>
 *   <li>{@code capacity} — {@code 60} requests per window</li>
 *   <li>{@code window} — {@code PT1M} (1 minute), expressed as an ISO-8601 duration
 *       string in configuration files</li>
 * </ul>
 *
 * @see ai.xdigit.ragchatstorage.security.RateLimitService
 * @see ai.xdigit.ragchatstorage.security.RateLimitFilter
 */
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    @Positive
    private int capacity = 60;

    @NotNull
    private Duration window = Duration.ofMinutes(1);
}
