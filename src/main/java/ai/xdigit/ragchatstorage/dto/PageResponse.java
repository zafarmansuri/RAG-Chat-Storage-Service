package ai.xdigit.ragchatstorage.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated response envelope used by all list endpoints.
 *
 * <p>Wraps a Spring Data {@link Page} into a plain, serialisation-friendly record so
 * that the API contract is decoupled from Spring internals. Callers can use
 * {@code hasNext} to drive forward pagination without needing to compute
 * {@code page < totalPages - 1} themselves.
 *
 * @param <T>           the element type
 * @param content       the items on the current page (may be empty if beyond the last page)
 * @param page          zero-based current page index
 * @param size          maximum items per page as requested
 * @param totalElements total number of matching elements across all pages
 * @param totalPages    total number of available pages
 * @param hasNext       {@code true} when a next page exists
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    /**
     * Constructs a {@code PageResponse} from a Spring Data {@link Page}.
     *
     * @param <T>  element type
     * @param page the Spring Data page to adapt
     * @return populated response record
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
