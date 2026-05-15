package ai.xdigit.ragchatstorage.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc / OpenAPI 3 configuration for the RAG Chat Storage API.
 *
 * <p>Declares the API metadata shown in Swagger UI and registers the {@code apiKey}
 * security scheme so that every protected endpoint can display the lock icon and
 * allow interactive authentication directly in the browser.
 *
 * <p>The security scheme maps to the {@code X-API-Key} HTTP request header, matching
 * the default configured in {@link SecurityProperties}. Controllers that require
 * authentication reference the scheme by name with
 * {@code @SecurityRequirement(name = "apiKey")}.
 *
 * <p>Swagger UI is available at {@code /swagger-ui.html} and the OpenAPI spec
 * at {@code /v3/api-docs}. Both paths are permitted without authentication in
 * {@link ai.xdigit.ragchatstorage.security.SecurityConfig}.
 *
 * @see SecurityProperties
 * @see ai.xdigit.ragchatstorage.security.SecurityConfig
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "RAG Chat Storage API",
                version = "v1",
                description = "Persist chat sessions and messages for a RAG chatbot."
        ),
        security = @SecurityRequirement(name = "apiKey")
)
@SecurityScheme(
        name = "apiKey",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key"
)
public class OpenApiConfig {
}
