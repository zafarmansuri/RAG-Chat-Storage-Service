package ai.xdigit.ragchatstorage.exception;

import ai.xdigit.ragchatstorage.config.RequestTraceContext;
import ai.xdigit.ragchatstorage.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for every handler method and both branches of {@code logHandled}
 * in {@link GlobalExceptionHandler}. Uses a plain {@link MockHttpServletRequest}
 * so no Spring context is needed, keeping the tests fast.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/v1/test");
        // Seed trace attributes so logHandled can read them without NPE
        request.setAttribute(RequestTraceContext.REQUEST_ID_ATTRIBUTE, "req-unit");
        request.setAttribute(RequestTraceContext.CORRELATION_ID_ATTRIBUTE, "corr-unit");
        request.setAttribute(RequestTraceContext.API_PATH_ATTRIBUTE, "/api/v1/test");
    }

    // -----------------------------------------------------------------------
    // handleValidation — MethodArgumentNotValidException
    // -----------------------------------------------------------------------

    @Test
    void handleValidationReturns400WithValidationErrorCode() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "userId", "is required"));
        bindingResult.addError(new FieldError("target", "title", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        // Both field errors should appear in the message
        assertThat(response.getBody().message()).contains("userId").contains("title");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/test");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleValidationJoinsMultipleFieldErrorsWithSemicolon() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "fieldA", "msg1"));
        bindingResult.addError(new FieldError("target", "fieldB", "msg2"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getBody().message()).contains("; ");
    }

    // -----------------------------------------------------------------------
    // handleConstraintViolation — ConstraintViolationException
    // -----------------------------------------------------------------------

    @Test
    void handleConstraintViolationReturns400WithValidationErrorCode() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("listSessions.userId");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("is required");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));
        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("userId").contains("is required");
    }

    @Test
    void handleConstraintViolationJoinsMultipleViolations() {
        ConstraintViolation<?> v1 = mock(ConstraintViolation.class);
        Path p1 = mock(Path.class);
        when(p1.toString()).thenReturn("param.a");
        when(v1.getPropertyPath()).thenReturn(p1);
        when(v1.getMessage()).thenReturn("err1");

        ConstraintViolation<?> v2 = mock(ConstraintViolation.class);
        Path p2 = mock(Path.class);
        when(p2.toString()).thenReturn("param.b");
        when(v2.getPropertyPath()).thenReturn(p2);
        when(v2.getMessage()).thenReturn("err2");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(v1, v2));
        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getBody().message()).contains("; ");
    }

    // -----------------------------------------------------------------------
    // handleMissingParam — MissingServletRequestParameterException
    // -----------------------------------------------------------------------

    @Test
    void handleMissingParamReturns400WithInvalidParameterCode() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("userId", "String");

        ResponseEntity<ApiErrorResponse> response = handler.handleMissingParam(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
        assertThat(response.getBody().message()).contains("userId");
    }

    // -----------------------------------------------------------------------
    // handleTypeMismatch — MethodArgumentTypeMismatchException
    // -----------------------------------------------------------------------

    @Test
    void handleTypeMismatchReturns400WithInvalidParameterCode() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("sessionId");

        ResponseEntity<ApiErrorResponse> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
        assertThat(response.getBody().message()).contains("sessionId");
    }

    // -----------------------------------------------------------------------
    // handleUnreadableBody — HttpMessageNotReadableException
    // -----------------------------------------------------------------------

    @Test
    void handleUnreadableBodyReturns400WithInvalidRequestBodyCode() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Cannot deserialize", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableBody(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST_BODY");
        assertThat(response.getBody().message()).isEqualTo("Request body is malformed or contains invalid values");
    }

    // -----------------------------------------------------------------------
    // handleBadRequest — BadRequestException  (4xx → WARN branch in logHandled)
    // -----------------------------------------------------------------------

    @Test
    void handleBadRequestReturns400WithBadRequestCode() {
        BadRequestException ex = new BadRequestException("title must not be blank");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("title must not be blank");
    }

    // -----------------------------------------------------------------------
    // handleNotFound — ResourceNotFoundException
    // -----------------------------------------------------------------------

    @Test
    void handleNotFoundReturns404WithResourceNotFoundCode() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Session abc not found");

        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Session abc not found");
    }

    // -----------------------------------------------------------------------
    // handleMissingStaticResource — NoResourceFoundException
    // -----------------------------------------------------------------------

    @Test
    void handleMissingStaticResourceReturns404WithResourceNotFoundCode() {
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/unknown/path");

        ResponseEntity<ApiErrorResponse> response = handler.handleMissingStaticResource(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Requested resource was not found");
    }

    // -----------------------------------------------------------------------
    // handleDataIntegrity — DataIntegrityViolationException
    // -----------------------------------------------------------------------

    @Test
    void handleDataIntegrityReturns400WithDataIntegrityErrorCode() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("constraint violation");

        ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("DATA_INTEGRITY_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Request could not be persisted");
    }

    // -----------------------------------------------------------------------
    // handleUnexpected — catch-all Exception  (5xx → ERROR branch in logHandled)
    // -----------------------------------------------------------------------

    @Test
    void handleUnexpectedReturns500WithInternalServerErrorCode() {
        RuntimeException ex = new RuntimeException("something blew up");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void handleUnexpectedIncludesPathInBody() {
        request.setRequestURI("/api/v1/sessions/abc");
        request.setAttribute(RequestTraceContext.API_PATH_ATTRIBUTE, "/api/v1/sessions/abc");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(new RuntimeException("boom"), request);

        assertThat(response.getBody().path()).isEqualTo("/api/v1/sessions/abc");
    }

    // -----------------------------------------------------------------------
    // Verify both log-level branches fire without exception
    // (4xx = WARN already covered above; 5xx = ERROR covered by handleUnexpected)
    // Extra: a 404 to explicitly exercise the WARN path
    // -----------------------------------------------------------------------

    @Test
    void handlerDoesNotThrowWhenTraceAttributesAreMissing() {
        // Request without any trace attributes — logHandled must fall back gracefully
        MockHttpServletRequest bare = new MockHttpServletRequest("POST", "/api/v1/sessions");
        BadRequestException ex = new BadRequestException("oops");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(ex, bare);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
