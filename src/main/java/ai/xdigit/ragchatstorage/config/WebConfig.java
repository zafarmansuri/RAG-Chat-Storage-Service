package ai.xdigit.ragchatstorage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Web layer configuration that defines the application's CORS policy.
 *
 * <p>The {@link CorsConfigurationSource} bean is picked up automatically by Spring
 * Security when {@code .cors(Customizer.withDefaults())} is called in
 * {@link ai.xdigit.ragchatstorage.security.SecurityConfig}. This keeps CORS policy
 * in one place rather than spread across both the MVC and Security configurations.
 *
 * <p>Allowed methods are limited to those actually used by the API:
 * {@code GET}, {@code POST}, {@code PATCH}, {@code DELETE}, and {@code OPTIONS}
 * (preflight). Credentials are not allowed because the service is stateless and
 * uses header-based API-key authentication rather than cookies.
 *
 * <p>The {@code X-Correlation-Id} header is exposed to browser clients so that
 * JavaScript can read it from responses for distributed tracing.
 *
 * @see CorsProperties
 * @see ai.xdigit.ragchatstorage.security.SecurityConfig
 */
@Configuration
public class WebConfig {

    /**
     * Builds the CORS configuration source applied globally to all routes.
     *
     * <p>Allowed origins are read from {@link CorsProperties#getAllowedOriginsList()},
     * which splits the comma-separated {@code app.cors.allowed-origins} property.
     * The preflight cache is set to 3600 seconds (1 hour) to reduce OPTIONS traffic.
     *
     * @param corsProperties the bound CORS configuration properties
     * @return a {@link CorsConfigurationSource} covering {@code /**}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOriginsList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-API-Key", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
