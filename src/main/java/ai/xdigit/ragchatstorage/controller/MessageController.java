package ai.xdigit.ragchatstorage.controller;

import ai.xdigit.ragchatstorage.dto.CreateMessageRequest;
import ai.xdigit.ragchatstorage.dto.MessageResponse;
import ai.xdigit.ragchatstorage.dto.PageResponse;
import ai.xdigit.ragchatstorage.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for chat message operations within a session.
 *
 * <p>All endpoints are nested under {@code /api/v1/sessions/{sessionId}/messages}
 * and require a valid {@code X-API-Key} header. Ownership of the parent session is
 * enforced by the service layer: passing a {@code userId} that does not match the
 * session owner returns HTTP 404.
 *
 * <p>HTTP status mapping:
 * <ul>
 *   <li>{@code POST /} — 201 Created with the new {@link MessageResponse}</li>
 *   <li>{@code GET /} — 200 OK with a chronologically ordered paginated list</li>
 * </ul>
 *
 * @see MessageService
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions/{sessionId}/messages")
@Tag(name = "Messages", description = "Chat message management APIs")
@SecurityRequirement(name = "apiKey")
public class MessageController {

    private final MessageService messageService;

    /**
     * Appends a new message to the specified session.
     *
     * <p>The {@code sender} field is required and must be either {@code USER} or
     * {@code ASSISTANT}. The optional {@code retrievedContext} field stores the RAG
     * context injected into an assistant response; blank values are normalised to
     * {@code null}.
     *
     * <p>Adding a message also advances the parent session's {@code updatedAt}
     * timestamp so it re-sorts to the top of the session list.
     *
     * @param sessionId the UUID of the parent session
     * @param userId    the caller's user ID; must match the session owner
     * @param request   the message creation request
     * @return HTTP 201 with the persisted {@link MessageResponse}
     */
    @PostMapping
    @Operation(summary = "Append a message to a session")
    public ResponseEntity<MessageResponse> addMessage(
            @PathVariable UUID sessionId,
            @RequestParam
            @NotBlank(message = "userId is required")
            @Size(max = 100, message = "userId must be at most 100 characters")
            String userId,
            @Valid @RequestBody CreateMessageRequest request
    ) {
        MessageResponse response = messageService.addMessage(sessionId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns a paginated, chronologically ordered message history for a session.
     *
     * <p>Messages are sorted by {@code createdAt ASC, id ASC} to preserve the
     * correct conversational sequence. The first page ({@code page=0}) contains the
     * oldest messages; subsequent pages move forward in time.
     *
     * @param sessionId the UUID of the parent session
     * @param userId    the caller's user ID; must match the session owner
     * @param page      zero-based page index (default 0)
     * @param size      page size, 1–100 (default 20)
     * @return HTTP 200 with a {@link PageResponse} of {@link MessageResponse}
     */
    @GetMapping
    @Operation(summary = "Retrieve paginated message history for a session")
    public PageResponse<MessageResponse> listMessages(
            @PathVariable UUID sessionId,
            @RequestParam
            @NotBlank(message = "userId is required")
            @Size(max = 100, message = "userId must be at most 100 characters")
            String userId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be greater than or equal to 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100")
            int size
    ) {
        return messageService.listMessages(sessionId, userId, page, size);
    }
}
