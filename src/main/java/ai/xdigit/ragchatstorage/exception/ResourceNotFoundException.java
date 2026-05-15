package ai.xdigit.ragchatstorage.exception;

import lombok.experimental.StandardException;

/**
 * Thrown when a requested resource does not exist or does not belong to the
 * requesting user. Intentionally indistinguishable to the caller (both cases return
 * HTTP 404) to avoid leaking information about other users' session IDs.
 *
 * <p>Caught by {@link GlobalExceptionHandler#handleNotFound}, which maps it to
 * HTTP 404 with error code {@code RESOURCE_NOT_FOUND}.
 *
 * @see GlobalExceptionHandler
 */
@StandardException
public class ResourceNotFoundException extends RuntimeException {
}
