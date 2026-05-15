package ai.xdigit.ragchatstorage.security;

import ai.xdigit.ragchatstorage.config.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the RAG Chat Storage Service.
 *
 * <p>Configures a stateless, session-free security chain with two custom filters:
 * <ol>
 *   <li>{@link ApiKeyAuthenticationFilter} — validates the {@code X-API-Key} header
 *       and populates the {@link org.springframework.security.core.context.SecurityContextHolder}.</li>
 *   <li>{@link RateLimitFilter} — enforces per-key request quotas, writing HTTP 429
 *       when the bucket is exhausted.</li>
 * </ol>
 *
 * <p>The following paths are publicly accessible without an API key:
 * <ul>
 *   <li>Spring's common static resource locations (classpath:{@code /static/}, etc.)</li>
 *   <li>{@code /}, {@code /index.html} — the interactive single-page UI</li>
 *   <li>{@code /swagger-ui.html}, {@code /swagger-ui/**}, {@code /v3/api-docs/**} — API docs</li>
 *   <li>{@code /actuator/health/**}, {@code /actuator/info/**} — Kubernetes probes</li>
 *   <li>{@code /h2-console/**} — embedded database console (same-origin only)</li>
 *   <li>HTTP {@code OPTIONS} preflight requests — CORS support</li>
 * </ul>
 *
 * <p>CSRF is disabled because the service is stateless (no cookies) and relies on
 * the API key for authentication. {@code frameOptions} is set to {@code sameOrigin}
 * to allow the H2 console to render inside an iframe on the same host.
 *
 * @see ApiKeyAuthenticationFilter
 * @see RateLimitFilter
 * @see SecurityProperties
 */
@Configuration
public class SecurityConfig {

    /**
     * Builds and returns the application's security filter chain.
     *
     * @param http               the Spring Security HTTP builder
     * @param securityProperties bound security configuration (API key header name and value)
     * @param rateLimitService   the rate-limit token-bucket service
     * @param objectMapper       Jackson mapper injected into the custom filters for error serialisation
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if filter chain construction fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProperties securityProperties,
            RateLimitService rateLimitService,
            ObjectMapper objectMapper
    ) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = new ApiKeyAuthenticationFilter(
                securityProperties.getApiKeyHeader(),
                securityProperties.getApiKey(),
                objectMapper
        );
        RateLimitFilter rateLimitFilter = new RateLimitFilter(
                securityProperties.getApiKeyHeader(),
                rateLimitService,
                objectMapper
        );

        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers("/h2-console", "/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/", "/index.html", "/app.css", "/app.js", "/favicon.ico").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }
}
