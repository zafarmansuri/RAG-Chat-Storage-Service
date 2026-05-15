package ai.xdigit.ragchatstorage.exception;

import ai.xdigit.ragchatstorage.config.RequestTraceContext;
import ai.xdigit.ragchatstorage.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralised exception-to-HTTP-response mapping for all REST controllers.
 *
 * <p>Every exception that escapes a controller is caught here and translated into a
 * consistent {@link ApiErrorResponse} body so that clients have a single contract for
 * error handling regardless of the failure type.
 *
 * <p>Mapped exception types and their HTTP status codes:
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} → 400 {@code VALIDATION_ERROR}</li>
 *   <li>{@link ConstraintViolationException} → 400 {@code VALIDATION_ERROR}</li>
 *   <li>{@link MethodArgumentTypeMismatchException} → 400 {@code INVALID_PARAMETER}</li>
 *   <li>{@link HttpMessageNotReadableException} → 400 {@code INVALID_REQUEST_BODY}</li>
 *   <li>{@link BadRequestException} → 400 {@code BAD_REQUEST}</li>
 *   <li>{@link ResourceNotFoundException} → 404 {@code RESOURCE_NOT_FOUND}</li>
 *   <li>{@link NoResourceFoundException} → 404 {@code RESOURCE_NOT_FOUND}</li>
 *   <li>{@link DataIntegrityViolationException} → 400 {@code DATA_INTEGRITY_ERROR}</li>
 *   <li>{@link Exception} (catch-all) → 500 {@code INTERNAL_SERVER_ERROR}</li>
 * </ul>
 *
 * <p>All responses are logged at {@code WARN} (4xx) or {@code ERROR} (5xx) level,
 * tagged with the request's correlation ID for distributed tracing.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles Jakarta Bean Validation failures on {@code @RequestBody} arguments.
     * Collects all field-level errors into a single semicolon-separated message.
     *
     * @param exception the validation exception thrown by Spring MVC
     * @param request   the current HTTP request
     * @return 400 response with code {@code VALIDATION_ERROR}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream().map(this::formatFieldError).collect(Collectors.joining("; "));

        logHandled(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, exception, request);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    /**
     * Handles constraint violations on {@code @RequestParam} and {@code @PathVariable}
     * arguments, triggered by {@code @Validated} on the controller class.
     *
     * @param exception the constraint violation exception
     * @param request   the current HTTP request
     * @return 400 response with code {@code VALIDATION_ERROR}
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        String message = exception.getConstraintViolations().stream().map(violation -> violation.getPropertyPath() + " " + violation.getMessage()).collect(Collectors.joining("; "));

        logHandled(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, exception, request);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    /**
     * Handles requests that omit a required {@code @RequestParam}.
     *
     * @param exception the missing parameter exception
     * @param request   the current HTTP request
     * @return 400 response with code {@code INVALID_PARAMETER}
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException exception, HttpServletRequest request) {
        String message = "Required parameter '%s' is missing".formatted(exception.getParameterName());
        logHandled(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, exception, request);
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, request);
    }

    /**
     * Handles type conversion failures on path or query parameters — for example,
     * passing a non-UUID string where a {@link java.util.UUID} is expected.
     *
     * @param exception the type mismatch exception
     * @param request   the current HTTP request
     * @return 400 response with code {@code INVALID_PARAMETER}
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        String message = "Invalid value for parameter '%s'".formatted(exception.getName());
        logHandled(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, exception, request);
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, request);
    }

    /**
     * Handles malformed JSON request bodies or invalid enum values that cannot be
     * deserialized by Jackson.
     *
     * @param exception the unreadable message exception
     * @param request   the current HTTP request
     * @return 400 response with code {@code INVALID_REQUEST_BODY}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        String message = "Request body is malformed or contains invalid values";
        logHandled(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", message, exception, request);
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", message, request);
    }

    /**
     * Handles business-rule violations raised explicitly by service layer code.
     *
     * @param exception the bad request exception
     * @param request   the current HTTP request
     * @return 400 response with code {@code BAD_REQUEST}
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException exception, HttpServletRequest request) {
        logHandled(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage(), exception, request);
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage(), request);
    }

    /**
     * Handles resource-not-found conditions, including attempts to access another
     * user's resources (ownership violations are reported as 404 to avoid leaking IDs).
     *
     * @param exception the not-found exception
     * @param request   the current HTTP request
     * @return 404 response with code {@code RESOURCE_NOT_FOUND}
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        logHandled(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), exception, request);
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request);
    }

    /**
     * Handles requests for static resources or API paths that do not exist, producing
     * a structured JSON 404 rather than the default Spring Whitelabel error page.
     *
     * @param exception the missing resource exception thrown by Spring MVC
     * @param request   the current HTTP request
     * @return 404 response with code {@code RESOURCE_NOT_FOUND}
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingStaticResource(NoResourceFoundException exception, HttpServletRequest request) {
        String message = "Requested resource was not found";
        logHandled(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message, exception, request);
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message, request);
    }

    /**
     * Handles database constraint violations (e.g. unique key conflicts) that escape
     * the service layer.
     *
     * @param exception the data integrity exception
     * @param request   the current HTTP request
     * @return 400 response with code {@code DATA_INTEGRITY_ERROR}
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        String message = "Request could not be persisted";
        logHandled(HttpStatus.BAD_REQUEST, "DATA_INTEGRITY_ERROR", message, exception, request);
        return build(HttpStatus.BAD_REQUEST, "DATA_INTEGRITY_ERROR", message, request);
    }

    /**
     * Catch-all handler for any unrecognised exception. Logs the full stack trace at
     * ERROR level and returns a generic 500 response without leaking internal details.
     *
     * @param exception the unexpected exception
     * @param request   the current HTTP request
     * @return 500 response with code {@code INTERNAL_SERVER_ERROR}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        logHandled(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred", exception, request);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred", request);
    }

    /**
     * Formats a single field error as {@code "<fieldName> <message>"}.
     */
    private String formatFieldError(FieldError fieldError) {
        return "%s %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

    /**
     * Logs the handled error at the appropriate level using structured key=value pairs
     * that include the request's correlation and request IDs.
     */
    private void logHandled(HttpStatus status, String code, String message, Exception exception, HttpServletRequest request) {
        String requestId = RequestTraceContext.getRequestId(request);
        String correlationId = RequestTraceContext.getCorrelationId(request);
        String apiPath = RequestTraceContext.getApiPath(request);
        String logMessage = "request.failed requestId={} correlationId={} method={} apiPath={} status={} code={} error={}";

        if (status.is5xxServerError()) {
            log.error(logMessage, requestId, correlationId, request.getMethod(), apiPath, status.value(), code, message, exception);
            return;
        }

        log.warn(logMessage, requestId, correlationId, request.getMethod(), apiPath, status.value(), code, message);
    }

    /**
     * Builds the {@link ResponseEntity} wrapping an {@link ApiErrorResponse}.
     */
    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        ApiErrorResponse error = new ApiErrorResponse(code, message, Instant.now(), request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
