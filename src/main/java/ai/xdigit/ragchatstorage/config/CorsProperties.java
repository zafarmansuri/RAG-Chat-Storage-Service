package ai.xdigit.ragchatstorage.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.stream.Stream;

/**
 * Bound configuration properties for CORS (Cross-Origin Resource Sharing).
 *
 * <p>Properties are read from the {@code app.cors} prefix in
 * {@code application.properties} / {@code application.yml} or the corresponding
 * environment variables (e.g., {@code APP_CORS_ALLOWED_ORIGINS}).
 *
 * <p>The {@code allowedOrigins} value is a comma-separated string, which makes it
 * easy to supply multiple origins through a single environment variable without
 * needing YAML list syntax. Use {@link #getAllowedOriginsList()} when passing the
 * value to Spring's {@link org.springframework.web.cors.CorsConfiguration}.
 *
 * <p>Default value: {@code http://localhost:3000} (suitable for local frontend
 * development; must be overridden in production).
 *
 * @see WebConfig
 */
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    @NotBlank
    private String allowedOrigins = "http://localhost:3000";

    /**
     * Splits the raw allowed-origins string on commas, trims each value, and
     * filters out blank entries.
     *
     * @return an immutable list of individual origin strings
     */
    public List<String> getAllowedOriginsList() {
        return Stream.of(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}