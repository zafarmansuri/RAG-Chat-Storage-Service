# RAG Chat Storage Service — API Documentation

**Base URL:** `http://localhost:8080`
**API Version:** v1
**Auth:** All `/api/v1/**` endpoints require `X-API-Key: <your-key>` header.

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [Rate Limiting](#2-rate-limiting)
3. [Request Tracing](#3-request-tracing)
4. [Common Response Shapes](#4-common-response-shapes)
5. [Sessions API](#5-sessions-api)
6. [Messages API](#6-messages-api)
7. [Health API](#7-health-api)
8. [Public Endpoints](#8-public-endpoints)
9. [Error Reference](#9-error-reference)

---

## 1. Authentication

All `/api/v1/**` endpoints are protected by a static API key sent as an HTTP header.

```
X-API-Key: <your-key>
```

The header name is configurable via the `API_KEY_HEADER` environment variable (default: `X-API-Key`). The key value is compared using **constant-time equality** to prevent timing attacks.

**On success:** the request proceeds normally.

**On failure — `401 Unauthorized`:**

```json
{
  "code": "UNAUTHORIZED",
  "message": "Missing or invalid API key",
  "timestamp": "2026-05-16T10:00:00Z",
  "path": "/api/v1/sessions"
}
```

---

## 2. Rate Limiting

A **fixed-window token bucket** is applied per API key after authentication.

**Default limits:** 60 requests per minute per key.

### Response headers (every allowed request)

| Header | Value | Description |
|---|---|---|
| `X-Rate-Limit-Limit` | integer | Maximum requests allowed per window |
| `X-Rate-Limit-Remaining` | integer | Requests remaining in the current window |

### On quota exhaustion — `429 Too Many Requests`

| Header | Value | Description |
|---|---|---|
| `Retry-After` | integer (seconds) | Time until the window resets |

```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests",
  "timestamp": "2026-05-16T10:00:00Z",
  "path": "/api/v1/sessions"
}
```

Configure via env vars: `RATE_LIMIT_CAPACITY=120`, `RATE_LIMIT_WINDOW=PT2M`

---

## 3. Request Tracing

Every request, regardless of outcome, receives two tracing identifiers echoed in the response.

| Header | Behaviour |
|---|---|
| `X-Request-Id` | Random UUID generated per request; caller may supply their own |
| `X-Correlation-Id` | Taken from the caller's header; falls back to `X-Request-Id` |

Pass `X-Correlation-Id` from your API gateway to correlate calls across multiple services in distributed logs.

---

## 4. Common Response Shapes

### SessionResponse

Returned by all session endpoints.

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Unique session identifier |
| `userId` | string | Identifier of the owning user |
| `title` | string | Human-readable session title |
| `favorite` | boolean | Whether the session is starred |
| `messageCount` | integer | Total messages in this session |
| `createdAt` | ISO-8601 timestamp | UTC creation time |
| `updatedAt` | ISO-8601 timestamp | UTC time of last modification (rename, favorite toggle, or new message) |

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "userId": "user-123",
  "title": "My research chat",
  "favorite": false,
  "messageCount": 4,
  "createdAt": "2026-05-16T10:00:00Z",
  "updatedAt": "2026-05-16T10:05:00Z"
}
```

### MessageResponse

Returned by all message endpoints.

| Field | Type | Description |
|---|---|---|
| `id` | UUID | Unique message identifier |
| `sessionId` | UUID | Parent session identifier |
| `sender` | `"USER"` or `"ASSISTANT"` | Who authored the message |
| `content` | string | Message body text |
| `retrievedContext` | string or `null` | RAG pipeline chunks used during generation; `null` for USER messages or ASSISTANT messages without retrieval |
| `createdAt` | ISO-8601 timestamp | UTC time the message was persisted |

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "sender": "ASSISTANT",
  "content": "Based on the retrieved documents...",
  "retrievedContext": "{\"source\":\"kb-doc-42\",\"chunks\":3}",
  "createdAt": "2026-05-16T10:01:05Z"
}
```

### PageResponse\<T\>

Generic paginated envelope used by all list endpoints.

| Field | Type | Description |
|---|---|---|
| `content` | array | Items on the current page (empty array if beyond last page) |
| `page` | integer | Zero-based current page index |
| `size` | integer | Requested page size |
| `totalElements` | integer | Total matching items across all pages |
| `totalPages` | integer | Total number of available pages |
| `hasNext` | boolean | `true` when a next page exists |

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "hasNext": true
}
```

### ApiErrorResponse

Consistent error envelope returned for all non-2xx responses.

| Field | Type | Description |
|---|---|---|
| `code` | string | Machine-readable error code |
| `message` | string | Human-readable description |
| `timestamp` | ISO-8601 timestamp | UTC time the error was generated |
| `path` | string | Request URI that triggered the error |

---

## 5. Sessions API

### 5.1 Create a Session

```
POST /api/v1/sessions
```

**Headers:**

| Header | Required | Value |
|---|---|---|
| `Content-Type` | yes | `application/json` |
| `X-API-Key` | yes | your API key |

**Request body:**

| Field | Type | Required | Constraints | Default |
|---|---|---|---|---|
| `userId` | string | yes | 1–100 chars, non-blank | — |
| `title` | string | no | max 255 chars | `"Untitled Chat"` |

```json
{
  "userId": "user-123",
  "title": "My research chat"
}
```

**Responses:**

| Status | Description | Body |
|---|---|---|
| `201 Created` | Session created | `SessionResponse` |
| `400 Bad Request` | Validation failure | `ApiErrorResponse` |
| `401 Unauthorized` | Missing/invalid API key | `ApiErrorResponse` |
| `429 Too Many Requests` | Rate limit exceeded | `ApiErrorResponse` |

**Example response `201`:**

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "userId": "user-123",
  "title": "My research chat",
  "favorite": false,
  "messageCount": 0,
  "createdAt": "2026-05-16T10:00:00Z",
  "updatedAt": "2026-05-16T10:00:00Z"
}
```

---

### 5.2 List Sessions

```
GET /api/v1/sessions
```

**Headers:**

| Header | Required | Value |
|---|---|---|
| `X-API-Key` | yes | your API key |

**Query parameters:**

| Parameter | Type | Required | Default | Constraints | Description |
|---|---|---|---|---|---|
| `userId` | string | yes | — | 1–100 chars, non-blank | Filter sessions by owner |
| `favorite` | boolean | no | *(all)* | `true` or `false` | When present, filters by starred status |
| `page` | integer | no | `0` | ≥ 0 | Zero-based page index |
| `size` | integer | no | `20` | 1–100 | Items per page |

Results are sorted `updatedAt DESC` — most recently active session first.

**Responses:**

| Status | Description | Body |
|---|---|---|
| `200 OK` | Success (may have empty `content`) | `PageResponse<SessionResponse>` |
| `400 Bad Request` | Invalid parameter | `ApiErrorResponse` |
| `401 Unauthorized` | Missing/invalid API key | `ApiErrorResponse` |

**Example response `200`:**

```json
{
  "content": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "userId": "user-123",
      "title": "My research chat",
      "favorite": true,
      "messageCount": 12,
      "createdAt": "2026-05-16T10:00:00Z",
      "updatedAt": "2026-05-16T11:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

---

### 5.3 Update a Session

```
PATCH /api/v1/sessions/{sessionId}
```

Partial update — only supplied fields are changed. At least one field must be present.

**Path parameter:**

| Parameter | Type | Description |
|---|---|---|
| `sessionId` | UUID | Session to update |

**Query parameter:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `userId` | string | yes | Must match the session owner |

**Headers:**

| Header | Required | Value |
|---|---|---|
| `Content-Type` | yes | `application/json` |
| `X-API-Key` | yes | your API key |

**Request body** — at least one field required:

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `title` | string | no | max 255 chars; must not be blank when provided | Rename the session |
| `favorite` | boolean | no | `true` or `false` | Star or unstar the session |

```json
{ "title": "Renamed chat" }
```
```json
{ "favorite": true }
```
```json
{ "title": "Renamed and starred", "favorite": true }
```

**Responses:**

| Status | Description | Body |
|---|---|---|
| `200 OK` | Updated successfully | `SessionResponse` |
| `400 Bad Request` | Empty body, blank title, or validation failure | `ApiErrorResponse` |
| `401 Unauthorized` | Missing/invalid API key | `ApiErrorResponse` |
| `404 Not Found` | Session not found or wrong owner | `ApiErrorResponse` |

---

### 5.4 Delete a Session

```
DELETE /api/v1/sessions/{sessionId}
```

Permanently deletes the session and **all of its messages** (cascade).

**Path parameter:**

| Parameter | Type | Description |
|---|---|---|
| `sessionId` | UUID | Session to delete |

**Query parameter:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `userId` | string | yes | Must match the session owner |

**Headers:**

| Header | Required | Value |
|---|---|---|
| `X-API-Key` | yes | your API key |

**Responses:**

| Status | Description | Body |
|---|---|---|
| `204 No Content` | Deleted successfully | *(empty)* |
| `401 Unauthorized` | Missing/invalid API key | `ApiErrorResponse` |
| `404 Not Found` | Session not found or wrong owner | `ApiErrorResponse` |

---

## 6. Messages API

### 6.1 Add a Message

```
POST /api/v1/sessions/{sessionId}/messages
```

Appends a new message to the session. Also advances the session's `updatedAt` timestamp so it re-sorts to the top of the session list.

**Path parameter:**

| Parameter | Type | Description |
|---|---|---|
| `sessionId` | UUID | Parent session |

**Query parameter:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `userId` | string | yes | Must match the session owner |

**Headers:**

| Header | Required | Value |
|---|---|---|
| `Content-Type` | yes | `application/json` |
| `X-API-Key` | yes | your API key |

**Request body:**

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `sender` | string | yes | `"USER"` or `"ASSISTANT"` | Who authored the message |
| `content` | string | yes | 1–10,000 chars, non-blank | Message body text |
| `retrievedContext` | string | no | max 20,000 chars | RAG chunks injected during generation; blank values stored as `null` |

**USER message (no context):**

```json
{
  "sender": "USER",
  "content": "What did the retriever find about neural networks?"
}
```

**ASSISTANT message with RAG context:**

```json
{
  "sender": "ASSISTANT",
  "content": "Based on the retrieved documents, neural networks are...",
  "retrievedContext": "{\"source\": \"kb-doc-42\", \"chunks\": 3, \"score\": 0.91}"
}
```

**Responses:**

| Status | Description | Body |
|---|---|---|
| `201 Created` | Message persisted | `MessageResponse` |
| `400 Bad Request` | Validation failure (missing sender, blank content, etc.) | `ApiErrorResponse` |
| `401 Unauthorized` | Missing/invalid API key | `ApiErrorResponse` |
| `404 Not Found` | Session not found or wrong owner | `ApiErrorResponse` |
| `429 Too Many Requests` | Rate limit exceeded | `ApiErrorResponse` |

**Example response `201`:**

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "sender": "ASSISTANT",
  "content": "Based on the retrieved documents, neural networks are...",
  "retrievedContext": "{\"source\": \"kb-doc-42\", \"chunks\": 3, \"score\": 0.91}",
  "createdAt": "2026-05-16T10:01:05Z"
}
```

---

### 6.2 List Messages

```
GET /api/v1/sessions/{sessionId}/messages
```

Returns a paginated, chronologically ordered message history. Sorted `createdAt ASC, id ASC` — oldest message first.

**Path parameter:**

| Parameter | Type | Description |
|---|---|---|
| `sessionId` | UUID | Parent session |

**Query parameters:**

| Parameter | Type | Required | Default | Constraints | Description |
|---|---|---|---|---|---|
| `userId` | string | yes | — | 1–100 chars, non-blank | Must match the session owner |
| `page` | integer | no | `0` | ≥ 0 | Zero-based page index |
| `size` | integer | no | `20` | 1–100 | Items per page |

**Headers:**

| Header | Required | Value |
|---|---|---|
| `X-API-Key` | yes | your API key |

**Responses:**

| Status | Description | Body |
|---|---|---|
| `200 OK` | Success (empty `content` if session has no messages) | `PageResponse<MessageResponse>` |
| `400 Bad Request` | Invalid parameter | `ApiErrorResponse` |
| `401 Unauthorized` | Missing/invalid API key | `ApiErrorResponse` |
| `404 Not Found` | Session not found or wrong owner | `ApiErrorResponse` |

**Example response `200`:**

```json
{
  "content": [
    {
      "id": "aaa11111-0000-0000-0000-000000000001",
      "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "sender": "USER",
      "content": "What did the retriever find?",
      "retrievedContext": null,
      "createdAt": "2026-05-16T10:01:00Z"
    },
    {
      "id": "bbb22222-0000-0000-0000-000000000002",
      "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "sender": "ASSISTANT",
      "content": "Based on the retrieved documents...",
      "retrievedContext": "{\"source\": \"kb-doc-42\"}",
      "createdAt": "2026-05-16T10:01:05Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "hasNext": false
}
```

---

## 7. Health API

### 7.1 Application Health (authenticated)

```
GET /api/v1/health
```

Executes a `SELECT 1` DB probe and returns liveness + readiness as a single response.

**Headers:**

| Header | Required | Value |
|---|---|---|
| `X-API-Key` | yes | your API key |

**Responses:**

| Status | Condition | Body |
|---|---|---|
| `200 OK` | DB probe passed | `HealthResponse` with all `"UP"` |
| `503 Service Unavailable` | DB probe failed | `HealthResponse` with `"DOWN"` |

**Response body:**

| Field | Type | Values | Description |
|---|---|---|---|
| `status` | string | `"UP"` / `"DOWN"` | Aggregate — `"UP"` only when all probes pass |
| `liveness` | string | `"UP"` | Always `"UP"` when the JVM is running |
| `readiness` | string | `"UP"` / `"DOWN"` | `"DOWN"` when DB is unreachable |
| `timestamp` | ISO-8601 | — | UTC time of the health check |

**Healthy:**

```json
{
  "status": "UP",
  "liveness": "UP",
  "readiness": "UP",
  "timestamp": "2026-05-16T10:00:00Z"
}
```

**DB unreachable:**

```json
{
  "status": "DOWN",
  "liveness": "UP",
  "readiness": "DOWN",
  "timestamp": "2026-05-16T10:00:00Z"
}
```

---

### 7.2 Actuator Health (public)

```
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
GET /actuator/info
```

No API key required. Standard Spring Boot Actuator endpoints suitable for Kubernetes probes.

**Example `GET /actuator/health`:**

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "H2", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP" },
    "livenessState": { "status": "UP" },
    "readinessState": { "status": "UP" }
  }
}
```

---

## 8. Public Endpoints

These paths require no API key and are not subject to rate limiting.

| Path | Description |
|---|---|
| `GET /` | Browser UI (single-page application) |
| `GET /index.html` | Browser UI |
| `GET /*.css`, `/*.js`, `/*.ico` | Static assets |
| `GET /swagger-ui.html` | Swagger UI |
| `GET /swagger-ui/**` | Swagger UI assets |
| `GET /v3/api-docs` | OpenAPI spec (JSON) |
| `GET /v3/api-docs/**` | OpenAPI spec sub-resources |
| `GET /actuator/health/**` | Spring Actuator health probes |
| `GET /actuator/info` | Application metadata |
| `GET /h2-console/**` | H2 web console (dev only) |
| `OPTIONS /**` | CORS preflight |

---

## 9. Error Reference

### Error codes

| Code | HTTP Status | Trigger |
|---|---|---|
| `UNAUTHORIZED` | 401 | Missing or invalid `X-API-Key` header |
| `RATE_LIMIT_EXCEEDED` | 429 | Per-key request quota exhausted |
| `VALIDATION_ERROR` | 400 | Jakarta Bean Validation failure on request body |
| `BAD_REQUEST` | 400 | Business rule violation — empty PATCH body, blank title on update |
| `INVALID_REQUEST_BODY` | 400 | Malformed JSON or invalid enum value (unknown `sender`) |
| `INVALID_PARAMETER` | 400 | Invalid or missing query / path parameter |
| `DATA_INTEGRITY_ERROR` | 400 | Database constraint violation |
| `RESOURCE_NOT_FOUND` | 404 | Session not found, or session owned by a different user |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected server-side error |

### Validation error detail

When `code` is `VALIDATION_ERROR`, the `message` field contains all field errors joined by `; `:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "userId is required; title must be at most 255 characters",
  "timestamp": "2026-05-16T10:00:00Z",
  "path": "/api/v1/sessions"
}
```

### Ownership note

Attempts to access a session with the wrong `userId` return `404 RESOURCE_NOT_FOUND` — not `403 Forbidden` — to prevent session ID enumeration across users.

---

## ER Diagram

```
┌─────────────────────────────────────────┐
│              chat_sessions              │
├─────────────────────────────────────────┤
│ id           UUID        PK NOT NULL    │
│ user_id      VARCHAR(100) NOT NULL      │
│ title        VARCHAR(255) NOT NULL      │
│ favorite     BOOLEAN      NOT NULL      │
│              DEFAULT FALSE              │
│ created_at   TIMESTAMPTZ  NOT NULL      │
│              DEFAULT CURRENT_TIMESTAMP  │
│ updated_at   TIMESTAMPTZ  NOT NULL      │
│              DEFAULT CURRENT_TIMESTAMP  │
└───────────────────┬─────────────────────┘
                    │ 1
                    │
                    │ CASCADE DELETE
                    │
                    │ N
┌───────────────────▼─────────────────────┐
│              chat_messages              │
├─────────────────────────────────────────┤
│ id                UUID        PK NOT NULL│
│ session_id        UUID        FK NOT NULL│ → chat_sessions.id
│ sender            VARCHAR(20) NOT NULL  │  ('USER' | 'ASSISTANT')
│ content           TEXT        NOT NULL  │
│ retrieved_context TEXT        NULL      │
│ created_at        TIMESTAMPTZ NOT NULL  │
│                   DEFAULT CURRENT_TIMESTAMP│
└─────────────────────────────────────────┘

INDEXES
  chat_sessions
    idx_chat_sessions_user_updated
      (user_id, updated_at DESC)               ← powers list-all query
    idx_chat_sessions_user_favorite_updated
      (user_id, favorite, updated_at DESC)     ← powers favorite-filter query

  chat_messages
    idx_chat_messages_session_created
      (session_id, created_at ASC)             ← powers message history query

CONSTRAINTS
  chat_messages.fk_chat_messages_session
    FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
    ON DELETE CASCADE
```

### Relationships

- **One** `chat_session` has **many** `chat_messages` (one-to-many)
- Deleting a session cascades and permanently removes all its messages
- Every `chat_message` belongs to exactly one `chat_session`
- Ownership is enforced in the service layer — all queries filter by both `id` and `user_id`

---

## Pagination Guide

All list endpoints return a `PageResponse<T>`. To walk all pages:

```
GET /api/v1/sessions?userId=user-123&page=0&size=20
  → hasNext: true

GET /api/v1/sessions?userId=user-123&page=1&size=20
  → hasNext: true

GET /api/v1/sessions?userId=user-123&page=2&size=20
  → hasNext: false   ← stop here
```

- First page is always `page=0`
- Maximum `size` is 100
- Use `totalElements` and `totalPages` to show progress indicators
- Use `hasNext` as the simplest "load more" condition

---

## Complete Lifecycle Example

This sequence creates a session, adds two messages (USER then ASSISTANT), renames the session, stars it, reads the history, then deletes it.

```bash
BASE=http://localhost:8080
KEY=change-me
USER=demo-user

# 1. Create session
SESSION=$(curl -s -X POST $BASE/api/v1/sessions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $KEY" \
  -d "{\"userId\":\"$USER\",\"title\":\"Test chat\"}" | jq -r .id)

echo "Session: $SESSION"

# 2. Add USER message
curl -s -X POST "$BASE/api/v1/sessions/$SESSION/messages?userId=$USER" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $KEY" \
  -d '{"sender":"USER","content":"What is RAG?"}' | jq .

# 3. Add ASSISTANT message with context
curl -s -X POST "$BASE/api/v1/sessions/$SESSION/messages?userId=$USER" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $KEY" \
  -d '{"sender":"ASSISTANT","content":"RAG stands for Retrieval-Augmented Generation...","retrievedContext":"{\"source\":\"kb-1\",\"chunks\":2}"}' | jq .

# 4. Rename
curl -s -X PATCH "$BASE/api/v1/sessions/$SESSION?userId=$USER" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $KEY" \
  -d '{"title":"RAG explained"}' | jq .

# 5. Star it
curl -s -X PATCH "$BASE/api/v1/sessions/$SESSION?userId=$USER" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $KEY" \
  -d '{"favorite":true}' | jq .

# 6. Read message history
curl -s "$BASE/api/v1/sessions/$SESSION/messages?userId=$USER" \
  -H "X-API-Key: $KEY" | jq .

# 7. Delete session (cascades to messages)
curl -s -X DELETE "$BASE/api/v1/sessions/$SESSION?userId=$USER" \
  -H "X-API-Key: $KEY"
echo "Deleted (expect 204)"

# 8. Verify it's gone
curl -s "$BASE/api/v1/sessions/$SESSION/messages?userId=$USER" \
  -H "X-API-Key: $KEY" | jq .
# → {"code":"RESOURCE_NOT_FOUND", ...}
```
