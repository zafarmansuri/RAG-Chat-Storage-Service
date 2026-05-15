package ai.xdigit.ragchatstorage.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring configuration that registers the request-tracing filter outside the
 * Spring Security filter chain.
 *
 * <p>{@link RequestTracingFilter} must run before every other filter — including
 * Spring Security's {@code DelegatingFilterProxy} — so that correlation IDs and
 * MDC keys are populated before any security or business logic executes.
 * Registering it via {@link FilterRegistrationBean} with
 * {@link Ordered#HIGHEST_PRECEDENCE} guarantees this ordering without relying on
 * Spring Security's internal filter ordering.
 *
 * @see RequestTracingFilter
 * @see RequestTraceContext
 */
@Configuration
public class LoggingConfig {

    /**
     * Registers {@link RequestTracingFilter} as the highest-priority servlet filter.
     *
     * <p>Using {@link Ordered#HIGHEST_PRECEDENCE} ensures the filter executes before
     * Spring Security so that request IDs and correlation IDs are available in MDC
     * for all downstream log statements, including security rejection messages.
     *
     * @return the configured {@link FilterRegistrationBean}
     */
    @Bean
    public FilterRegistrationBean<RequestTracingFilter> requestTracingFilterRegistration() {
        FilterRegistrationBean<RequestTracingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestTracingFilter());
        registration.setName("requestTracingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
