package ai.xdigit.ragchatstorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the RAG Chat Storage Service.
 *
 * <p>This Spring Boot application exposes a REST API for persisting chat sessions and
 * messages produced by a Retrieval-Augmented Generation (RAG) chatbot. It uses an
 * embedded H2 database (PostgreSQL-compatible mode), Flyway schema migrations, and
 * stateless API-key authentication.
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>CRUD for {@code ChatSession} entities, with optional favourite-flagging</li>
 *   <li>Append-only storage of {@code ChatMessage} entities, including an optional
 *       {@code retrievedContext} field that captures the RAG source chunks</li>
 *   <li>Sliding-window rate limiting per API key</li>
 *   <li>Correlation-ID tracing propagated through request/response headers</li>
 *   <li>Embedded Swagger UI served at {@code /swagger-ui.html}</li>
 *   <li>Interactive single-page UI served at {@code /}</li>
 * </ul>
 *
 * <p>{@code UserDetailsServiceAutoConfiguration} is excluded because authentication
 * is handled entirely by {@link ai.xdigit.ragchatstorage.security.ApiKeyAuthenticationFilter}.
 *
 * @see ai.xdigit.ragchatstorage.security.SecurityConfig
 * @see ai.xdigit.ragchatstorage.config.RateLimitProperties
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class RagChatStorageApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments forwarded to {@link SpringApplication}
     */
    public static void main(String[] args) {
        SpringApplication.run(RagChatStorageApplication.class, args);
    }
}
