package ai.xdigit.ragchatstorage.controller;

import ai.xdigit.ragchatstorage.dto.CreateSessionRequest;
import ai.xdigit.ragchatstorage.dto.PageResponse;
import ai.xdigit.ragchatstorage.dto.SessionResponse;
import ai.xdigit.ragchatstorage.dto.UpdateSessionRequest;
import ai.xdigit.ragchatstorage.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
 * REST controller for chat session lifecycle operations.
 *
 * <p>All endpoints are mounted under {@code /api/v1/sessions} and require a valid
 * {@code X-API-Key} header. The {@code userId} query parameter acts as the
 * ownership key — every operation is scoped to a single user.
 *
 * <p>Bean Validation ({@link Validated}) is applied at the controller layer for
 * request parameters. Body validation is handled via {@link Valid} on request
 * bodies; field-level constraints are declared on the DTO records.
 *
 * <p>HTTP status mapping:
 * <ul>
 *   <li>{@code POST /} — 201 Created</li>
 *   <li>{@code GET /} — 200 OK with paginated body</li>
 *   <li>{@code PATCH /{sessionId}} — 200 OK</li>
 *   <li>{@code DELETE /{sessionId}} — 204 No Content</li>
 * </ul>
 *
 * @see SessionService
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Chat session management APIs")
@SecurityRequirement(name = "apiKey")
public class SessionController {

    private final SessionService sessionService;

    /**
     * Creates a new chat session for the given user.
     *
     * <p>The {@code title} field in the request body is optional; when omitted or
     * blank it defaults to {@code "Untitled Chat"}.
     *
     * @param request the creation request; {@code userId} is required, {@code title} is optional
     * @return HTTP 201 with the created {@link SessionResponse}
     */
    @PostMapping
    @Operation(summary = "Create a chat session")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session created"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        SessionResponse response = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns a paginated list of sessions owned by the specified user.
     *
     * <p>The optional {@code favorite} parameter filters by the favorite flag.
     * When omitted all sessions are returned. Results are sorted by
     * {@code updatedAt DESC} so the most recently active session comes first.
     *
     * @param userId   the owner of the sessions; required, max 100 characters
     * @param favorite optional filter; {@code true} or {@code false}; omit for all
     * @param page     zero-based page index (default 0)
     * @param size     page size, 1–100 (default 20)
     * @return HTTP 200 with a {@link PageResponse} of {@link SessionResponse}
     */
    @GetMapping
    @Operation(summary = "List sessions for a user")
    public PageResponse<SessionResponse> listSessions(
            @RequestParam
            @NotBlank(message = "userId is required")
            @Size(max = 100, message = "userId must be at most 100 characters")
            @Parameter(description = "Owner of the sessions", required = true) String userId,
            @RequestParam(required = false)
            @Parameter(description = "Optional favorite filter") Boolean favorite,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be greater than or equal to 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") int size) {
        return sessionService.listSessions(userId, favorite, page, size);
    }

    /**
     * Updates the title and/or favorite flag of an existing session.
     *
     * <p>This is a partial update (PATCH): only the fields present in the request
     * body are modified. At least one of {@code title} or {@code favorite} must be
     * provided; an empty body returns HTTP 400.
     *
     * @param sessionId the UUID of the session to update
     * @param userId    the owner; must match the session's recorded owner
     * @param request   the patch payload; at least one non-null field required
     * @return HTTP 200 with the updated {@link SessionResponse}
     */
    @PatchMapping("/{sessionId}")
    @Operation(summary = "Rename a session and/or update its favorite flag")
    public SessionResponse updateSession(
            @PathVariable UUID sessionId,
            @RequestParam
            @NotBlank(message = "userId is required")
            @Size(max = 100, message = "userId must be at most 100 characters") String userId,
            @Valid @RequestBody UpdateSessionRequest request) {
        return sessionService.updateSession(sessionId, userId, request);
    }

    /**
     * Deletes a session and all of its messages.
     *
     * <p>Deletion cascades to all child messages via JPA. The operation is
     * idempotent in the sense that a second call to the same ID returns 404 (the
     * session is already gone).
     *
     * @param sessionId the UUID of the session to delete
     * @param userId    the owner; must match the session's recorded owner
     */
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a session and all of its messages")
    public void deleteSession(
            @PathVariable UUID sessionId,
            @RequestParam
            @NotBlank(message = "userId is required")
            @Size(max = 100, message = "userId must be at most 100 characters") String userId) {
        sessionService.deleteSession(sessionId, userId);
    }
}
