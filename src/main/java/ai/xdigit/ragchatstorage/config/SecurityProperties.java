package ai.xdigit.ragchatstorage.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound configuration properties for API-key authentication.
 *
 * <p>Properties are read from the {@code app.security} prefix in
 * {@code application.properties} / {@code application.yml} or the corresponding
 * environment variables (e.g., {@code APP_SECURITY_API_KEY}).
 *
 * <p>Both fields must be non-blank; the application will refuse to start if either
 * is missing or empty (validated via {@link Validated}).
 *
 * <p>Default values are provided for local development convenience:
 * <ul>
 *   <li>{@code apiKeyHeader} defaults to {@code X-API-Key}</li>
 *   <li>{@code apiKey} defaults to {@code change-me} — <strong>must be overridden
 *       in production</strong> via the {@code APP_SECURITY_API_KEY} environment
 *       variable or the {@code app.security.api-key} property.</li>
 * </ul>
 *
 * @see ai.xdigit.ragchatstorage.security.ApiKeyAuthenticationFilter
 * @see ai.xdigit.ragchatstorage.security.SecurityConfig
 */
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    @NotBlank
    private String apiKeyHeader = "X-API-Key";

    @NotBlank
    private String apiKey = "change-me";
}
