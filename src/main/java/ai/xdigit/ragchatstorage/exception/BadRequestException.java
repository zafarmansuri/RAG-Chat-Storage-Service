package ai.xdigit.ragchatstorage.exception;

import lombok.experimental.StandardException;

/**
 * Thrown when a request violates a business rule that cannot be expressed as a
 * Jakarta Bean Validation constraint — for example, submitting a PATCH body with
 * neither {@code title} nor {@code favorite}, or providing a blank title on update.
 *
 * <p>Caught by {@link GlobalExceptionHandler#handleBadRequest}, which maps it to
 * HTTP 400 with error code {@code BAD_REQUEST}.
 *
 * @see GlobalExceptionHandler
 */
@StandardException
public class BadRequestException extends RuntimeException {
}
