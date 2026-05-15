# RAG Chat Storage Service

A production-ready Spring Boot microservice for persisting chat sessions and messages in Retrieval-Augmented Generation (RAG) applications. Ships with a built-in browser UI, API-key authentication, fixed-window rate limiting, structured request tracing, Flyway schema migrations, Swagger UI, and full container support.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Quick Start — Docker Compose](#quick-start--docker-compose)
- [Quick Start — Podman Compose](#quick-start--podman-compose)
- [Quick Start — Local Maven](#quick-start--local-maven)
- [Configuration Reference](#configuration-reference)
- [API Reference](#api-reference)
- [Error Responses](#error-responses)
- [Security](#security)
- [Rate Limiting](#rate-limiting)
- [Browser UI](#browser-ui)
- [Swagger UI](#swagger-ui)
- [H2 Console](#h2-console)
- [Actuator Endpoints](#actuator-endpoints)
- [Request Tracing](#request-tracing)
- [Running Tests](#running-tests)
- [E2E Tests (Playwright)](#e2e-tests-playwright)
- [Postman Collection](#postman-collection)
- [Project Structure](#project-structure)
- [Example curl Commands](#example-curl-commands)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  Browser / API Client                                           │
│  X-API-Key: <your-key>                                          │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot (port 8080)                                        │
│                                                                 │
│  RequestTracingFilter  ──  MDC: requestId, correlationId        │
│  ApiKeyAuthenticationFilter  ──  validates X-API-Key header     │
│  RateLimitFilter  ──  fixed window, 60 req/min per key          │
│                                                                 │
│  SessionController    /api/v1/sessions                          │
│  MessageController    /api/v1/sessions/{id}/messages            │
│  HealthController     /api/v1/health                            │
│                                                                 │
│  SessionService  ──  MessageService  ──  HealthService          │
│                                                                 │
│  Spring Data JPA  ──  Flyway migrations (V1)                    │
│                                                                 │
│  H2 (file-backed, PostgreSQL-compat mode)                       │
│  /app/data/rag-chat.mv.db  (named Docker volume)                │
└─────────────────────────────────────────────────────────────────┘
```

### Request lifecycle

```
HTTP Request
    │
    ├─ RequestTracingFilter        assigns requestId + correlationId to MDC
    │
    ├─ ApiKeyAuthenticationFilter  validates X-API-Key header (constant-time compare)
    │                              → 401 UNAUTHORIZED on failure
    │
    ├─ RateLimitFilter             fixed-window token bucket per API key
    │                              → 429 RATE_LIMIT_EXCEEDED on exhaustion
    │
    ├─ Controller                  Bean Validation on params and body
    │                              → 400 VALIDATION_ERROR on failure
    │
    ├─ Service                     ownership check: sessionId + userId
    │                              → 404 RESOURCE_NOT_FOUND on mismatch
    │
    └─ Repository / H2             persistence
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Security | Spring Security (stateless, API key) |
| Persistence | Spring Data JPA + Hibernate |
| Database | H2 (file-backed, PostgreSQL-compat mode) |
| Migrations | Flyway |
| API Docs | SpringDoc OpenAPI 2.6.0 (Swagger UI) |
| Build | Maven 3.9 |
| Container | Docker / Podman (multi-stage Alpine image) |
| Testing | JUnit 5 + Spring Boot Test + Playwright |

**Why H2?**
H2 eliminates a second container dependency while remaining fully compatible with PostgreSQL SQL syntax via `MODE=PostgreSQL`. The Flyway migration and all JPA mappings are portable to a real PostgreSQL instance without changes.

---

## Prerequisites

| Tool | Minimum version | Required for |
|---|---|---|
| Docker **or** Podman | Latest stable | Container path |
| JDK | 21 | Local Maven path |
| Maven | 3.9+ | Local Maven path |
| Node.js | 18+ | Playwright E2E tests only |

---

## Quick Start — Docker Compose

### 1. Clone the repository

```bash
git clone <repo-url> rag-chat-storage-service
cd rag-chat-storage-service
```

### 2. (Optional) Create a `.env` file

```bash
cp .env.example .env
# Edit .env — at minimum, change API_KEY to a real secret
```

> **If you skip this step:** `docker compose up --build` still works using built-in defaults.
> The default `API_KEY=change-me` is intentionally weak — create `.env` before using in any shared environment.

### 3. Build and start

```bash
docker compose up --build
```

> Append `-d` to run detached.

> **First build:** Downloads ~200 Maven dependencies silently (`-q` flag). Takes 3–5 minutes. Subsequent builds use the layer cache and complete in seconds.

### 4. Verify

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP", ...}
```

### 5. Stop

```bash
docker compose down        # keeps the named H2 volume
docker compose down -v     # also removes the volume (data loss!)
```

---

## Quick Start — Podman Compose

> **Prerequisite:** Podman Desktop must be installed and the Podman machine must be running.
> Check with: `podman machine list`

> **One-time credential fix:** If you see `exec: "docker-credential-desktop": executable file not found`, remove the `"credsStore"` key from `~/.docker/config.json`.

### 1–2. Same as Docker Compose above.

### 3. Build and start

```bash
podman compose up --build
```

### Useful Podman commands

```bash
podman ps                                    # list running containers
podman logs -f rag-chat-storage-service      # tail application logs
podman exec -it rag-chat-storage-service sh  # shell into the container
podman stats                                 # live CPU/memory usage
podman machine list                          # check machine status
```

---

## Quick Start — Local Maven

### 1. Set the API key

```bash
export API_KEY=my-secret-key
# or source a .env file:
cp .env.example .env
export $(grep -v '^#' .env | xargs)
```

### 2. Start the application

```bash
mvn spring-boot:run
```

### 3. Verify

```bash
curl http://localhost:8080/actuator/health
curl -H 'X-API-Key: my-secret-key' http://localhost:8080/api/v1/health
```

---

## Configuration Reference

All settings have built-in defaults for local development. Override via environment variables or `.env` file.

| Environment variable | Spring property | Default | Description |
|---|---|---|---|
| `APP_PORT` | `server.port` | `8080` | HTTP listen port |
| `DB_PATH` | `spring.datasource.url` (path segment) | `./data/rag-chat` | H2 file path (without `.mv.db` extension) |
| `DB_USERNAME` | `spring.datasource.username` | `sa` | H2 JDBC username |
| `DB_PASSWORD` | `spring.datasource.password` | *(empty)* | H2 JDBC password |
| `H2_CONSOLE_ENABLED` | `spring.h2.console.enabled` | `true` | Enable H2 web console at `/h2-console` |
| `API_KEY_HEADER` | `app.security.api-key-header` | `X-API-Key` | Name of the authentication header |
| `API_KEY` | `app.security.api-key` | `change-me` | **Required in production** — the shared API secret |
| `CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | `http://localhost:3000` | Comma-separated list of allowed CORS origins |
| `RATE_LIMIT_CAPACITY` | `app.rate-limit.capacity` | `60` | Max requests per window per API key |
| `RATE_LIMIT_WINDOW` | `app.rate-limit.window` | `PT1M` | Rate-limit window as ISO-8601 duration |

### Production checklist

- Set `API_KEY` to a long random secret.
- Set `CORS_ALLOWED_ORIGINS` to your actual frontend origin(s).
- Set `H2_CONSOLE_ENABLED=false`.

---

## API Reference

Base URL: `http://localhost:8080`

All `/api/v1/**` endpoints require the API key:

```
X-API-Key: <your-key>
```

---

### Sessions

#### `POST /api/v1/sessions` — Create a session

**Request body:**

| Field | Type | Required | Constraints | Notes |
|---|---|---|---|---|
| `userId` | string | yes | max 100 chars | Identifies the session owner |
| `title` | string | no | max 255 chars | Defaults to `"Untitled Chat"` if blank/absent |

```json
{
  "userId": "user-123",
  "title": "My research chat"
}
```

**Response `201 Created`:**

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

#### `GET /api/v1/sessions` — List sessions

**Query parameters:**

| Parameter | Type | Required | Default | Constraints |
|---|---|---|---|---|
| `userId` | string | yes | — | max 100 chars |
| `favorite` | boolean | no | *(all)* | `true` or `false` |
| `page` | integer | no | `0` | ≥ 0 |
| `size` | integer | no | `20` | 1–100 |

Results are sorted by `updatedAt DESC` — most recently active session first.

**Response `200 OK`:**

```json
{
  "content": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "userId": "user-123",
      "title": "My research chat",
      "favorite": false,
      "messageCount": 4,
      "createdAt": "2026-05-16T10:00:00Z",
      "updatedAt": "2026-05-16T10:05:00Z"
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

#### `PATCH /api/v1/sessions/{sessionId}` — Update a session

**Path parameter:** `sessionId` (UUID)

**Query parameter:** `userId` (string, required)

**Request body** — at least one field required:

| Field | Type | Required | Constraints |
|---|---|---|---|
| `title` | string | no | max 255 chars; must not be blank when provided |
| `favorite` | boolean | no | `true` or `false` |

```json
{ "title": "Renamed chat" }
{ "favorite": true }
{ "title": "Renamed and starred", "favorite": true }
```

**Response `200 OK`:** Updated `SessionResponse` (same shape as create).

---

#### `DELETE /api/v1/sessions/{sessionId}` — Delete a session

**Path parameter:** `sessionId` (UUID)

**Query parameter:** `userId` (string, required)

Cascades to all messages belonging to the session.

**Response `204 No Content`** — empty body.

---

### Messages

#### `POST /api/v1/sessions/{sessionId}/messages` — Add a message

**Path parameter:** `sessionId` (UUID)

**Query parameter:** `userId` (string, required)

**Request body:**

| Field | Type | Required | Constraints | Notes |
|---|---|---|---|---|
| `sender` | string | yes | `"USER"` or `"ASSISTANT"` | Case-sensitive enum |
| `content` | string | yes | max 10,000 chars | Message body |
| `retrievedContext` | string | no | max 20,000 chars | RAG context chunks; blank is stored as `null` |

```json
{
  "sender": "USER",
  "content": "What did the retriever find about neural networks?"
}
```

```json
{
  "sender": "ASSISTANT",
  "content": "Based on the retrieved documents, neural networks are...",
  "retrievedContext": "{\"source\": \"kb-doc-42\", \"chunks\": 3, \"score\": 0.91}"
}
```

**Response `201 Created`:**

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "sender": "ASSISTANT",
  "content": "Based on the retrieved documents, neural networks are...",
  "retrievedContext": "{\"source\": \"kb-doc-42\", \"chunks\": 3, \"score\": 0.91}",
  "createdAt": "2026-05-16T10:01:00Z"
}
```

---

#### `GET /api/v1/sessions/{sessionId}/messages` — List messages

**Path parameter:** `sessionId` (UUID)

**Query parameters:**

| Parameter | Type | Required | Default | Constraints |
|---|---|---|---|---|
| `userId` | string | yes | — | max 100 chars |
| `page` | integer | no | `0` | ≥ 0 |
| `size` | integer | no | `20` | 1–100 |

Results are sorted `createdAt ASC, id ASC` — oldest message first, preserving conversational order.

**Response `200 OK`:**

```json
{
  "content": [
    {
      "id": "aaa...",
      "sessionId": "3fa85f64...",
      "sender": "USER",
      "content": "What did the retriever find?",
      "retrievedContext": null,
      "createdAt": "2026-05-16T10:01:00Z"
    },
    {
      "id": "bbb...",
      "sessionId": "3fa85f64...",
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

### Health

#### `GET /api/v1/health` — Application health (authenticated)

Requires `X-API-Key`. Returns HTTP 200 when all checks pass, HTTP 503 when the DB probe fails.

```json
{
  "status": "UP",
  "liveness": "UP",
  "readiness": "UP",
  "timestamp": "2026-05-16T10:00:00Z"
}
```

#### `GET /actuator/health` — Actuator health (public)

No API key required. Suitable for Kubernetes liveness/readiness probes.

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "livenessState": { "status": "UP" },
    "readinessState": { "status": "UP" }
  }
}
```

Sub-paths: `/actuator/health/liveness`, `/actuator/health/readiness`

---

## Error Responses

All errors use a consistent JSON envelope:

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "Session 3fa8... was not found for user user-123",
  "timestamp": "2026-05-16T10:00:00Z",
  "path": "/api/v1/sessions/3fa8..."
}
```

| HTTP status | `code` | Cause |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Bean Validation failure on request body fields |
| `400` | `BAD_REQUEST` | Business rule violated (e.g., empty PATCH body, blank title on update) |
| `400` | `INVALID_REQUEST_BODY` | Malformed JSON or invalid enum value (unknown `sender`) |
| `400` | `INVALID_PARAMETER` | Invalid or missing query/path parameter |
| `400` | `DATA_INTEGRITY_ERROR` | Database constraint violation |
| `401` | `UNAUTHORIZED` | Missing or invalid `X-API-Key` header |
| `404` | `RESOURCE_NOT_FOUND` | Session not found or owned by a different user |
| `429` | `RATE_LIMIT_EXCEEDED` | Per-key request quota exhausted; check `Retry-After` header |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected server error |

> **Ownership note:** Wrong-owner and not-found both return `404` to prevent session ID enumeration.

---

## Security

Authentication uses a static API key compared in **constant time** (`MessageDigest.isEqual`) to prevent timing-based side-channel attacks.

```
X-API-Key: my-secret-key
```

### Public paths (no API key required)

| Path | Purpose |
|---|---|
| `GET /`, `/index.html`, `/*.css`, `/*.js`, `/*.ico` | Browser UI |
| `GET /swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | API documentation |
| `GET /actuator/health/**`, `/actuator/info/**` | Kubernetes probes |
| `GET /h2-console/**` | Embedded DB console (same-origin only) |
| `OPTIONS /**` | CORS preflight |

All `/api/v1/**` routes are protected.

---

## Rate Limiting

A **fixed-window** rate limiter is applied per API key after authentication.

Default: **60 requests per minute** per key.

**Response headers on every allowed request:**

| Header | Description |
|---|---|
| `X-Rate-Limit-Limit` | Maximum requests allowed per window |
| `X-Rate-Limit-Remaining` | Requests remaining in the current window |

**On quota exhaustion — `HTTP 429`:**

| Header | Description |
|---|---|
| `Retry-After` | Seconds until the current window resets |

Configure via:

```dotenv
RATE_LIMIT_CAPACITY=120
RATE_LIMIT_WINDOW=PT2M
```

---

## Browser UI

Open `http://localhost:8080/` in any browser.

**Features:**
- Login page — enter User ID and API key; validated against the live service before proceeding
- Dark / Light theme — toggle in the top-right corner, persisted to `localStorage`
- Create, rename, favorite, and delete sessions from the left sidebar
- Filter sessions by **All** / **Starred**
- View message history for any session
- Compose `USER` and `ASSISTANT` messages with an optional RAG context field
- Click the RAG context badge on any message to inspect retrieved chunks in a side panel
- Live health indicator (polls every 30 s)
- Keyboard shortcuts: `N` new session · `Enter` send · `Shift+Enter` newline · `⌘K` clear view

---

## Swagger UI

Interactive API documentation — no API key required to browse, key needed to make authenticated calls.

```
http://localhost:8080/swagger-ui.html
```

Click **Authorize** and enter your API key to execute authenticated requests directly in the browser.

Raw OpenAPI spec (JSON):

```
http://localhost:8080/v3/api-docs
```

---

## H2 Console

Embedded browser-based database viewer — enabled by default in development.

```
http://localhost:8080/h2-console
```

| Run mode | JDBC URL |
|---|---|
| Local Maven | `jdbc:h2:file:./data/rag-chat` |
| Docker / Podman Compose | `jdbc:h2:file:/app/data/rag-chat` |

Username: `sa` · Password: *(leave blank)*

> Disable in production: `H2_CONSOLE_ENABLED=false`

---

## Actuator Endpoints

| Endpoint | Auth | Description |
|---|---|---|
| `GET /actuator/health` | None | Full health detail |
| `GET /actuator/health/liveness` | None | Kubernetes liveness probe |
| `GET /actuator/health/readiness` | None | Kubernetes readiness probe |
| `GET /actuator/info` | None | Application metadata |

---

## Request Tracing

Every request receives two tracing identifiers echoed in the response headers:

| Header | Behaviour |
|---|---|
| `X-Request-Id` | Generated as a random UUID if not supplied by the caller |
| `X-Correlation-Id` | Taken from the caller's header; falls back to `X-Request-Id` |

Both appear in every log line via SLF4J MDC:

```
2026-05-16T10:00:00.000Z [http-nio-8080-exec-1] INFO ... [req=abc-123 corr=xyz-456 path=/api/v1/sessions] - request.completed method=GET status=200 durationMs=4
```

Pass `X-Correlation-Id` from your gateway to correlate calls across services in distributed logs.

---

## Running Tests

### Unit + Integration Tests (JUnit 5)

```bash
mvn test
```

Uses in-memory H2 — no external dependencies. The suite contains **182 tests** covering unit, service, filter, security, and full Spring Boot integration layers.

```bash
# Run a specific class
mvn test -Dtest=SessionServiceExtendedTest

# Run a specific method
mvn test -Dtest=GlobalExceptionHandlerTest#handleUnexpectedReturns500WithInternalServerErrorCode
```

---

## E2E Tests (Playwright)

**33 end-to-end UI scenarios** in `e2e/rag-chat.spec.ts` covering: login, theme toggle, session CRUD, messaging, RAG context panel, keyboard shortcuts, pagination, starred filter, inline rename, and logout.

### Setup (first time only)

```bash
npm install
npx playwright install chromium
```

### Run against local Maven server

```bash
# Terminal 1
mvn spring-boot:run

# Terminal 2
npx playwright test --project=chromium
```

### Run against Docker / Podman Compose

```bash
docker compose up --build -d
npx playwright test --project=chromium
```

### View HTML report

```bash
npx playwright show-report
```

---

## Postman Collection

A comprehensive Postman collection with **87 requests** is included at the project root:

```
RAG_Chat_Storage.postman_collection.json
```

Import via **File → Import**, then set collection variables:

| Variable | Default | Description |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Base URL of the running service |
| `apiKey` | `change-me` | Must match `API_KEY` in your configuration |
| `userId` | `postman-user` | Owner ID used in all test requests |

| Folder | Coverage |
|---|---|
| 00 · Health | Liveness and auth checks |
| 01 · Sessions Create | Happy path, title defaults, validation boundaries |
| 02 · Sessions List | Pagination, favorite filter, edge cases |
| 03 · Sessions Update | Partial updates, ownership checks |
| 04 · Messages Create | Both sender types, context handling, validation boundaries |
| 05 · Messages List | Pagination, ordering |
| 06 · Sessions Delete | Cascade verification, 404 on double-delete |
| 07 · Security | Timing-safe key checks, injection in userId |
| 08 · Rate Limit | Header presence and decrement verification |
| 09 · Static / Swagger | Public path accessibility |
| 10 · End-to-End | Full lifecycle: create → message → update → favorite → delete → verify cascade |

---

## Project Structure

```
rag-chat-storage-service/
├── src/
│   ├── main/
│   │   ├── java/ai/xdigit/ragchatstorage/
│   │   │   ├── RagChatStorageApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── HealthController.java         GET /api/v1/health
│   │   │   │   ├── MessageController.java        POST/GET /api/v1/sessions/{id}/messages
│   │   │   │   └── SessionController.java        CRUD /api/v1/sessions
│   │   │   ├── service/
│   │   │   │   ├── HealthService.java            DB probe → HealthResponse
│   │   │   │   ├── MessageService.java           message business logic
│   │   │   │   └── SessionService.java           session business logic + ownership
│   │   │   ├── repository/
│   │   │   │   ├── ChatMessageRepository.java    JPA message queries
│   │   │   │   ├── ChatSessionRepository.java    JPA session queries + ownership
│   │   │   │   └── MessageCountView.java         batch count projection (avoids N+1)
│   │   │   ├── model/
│   │   │   │   ├── ChatMessage.java              message entity
│   │   │   │   ├── ChatSession.java              session entity (cascade to messages)
│   │   │   │   └── Sender.java                   USER / ASSISTANT enum
│   │   │   ├── dto/
│   │   │   │   ├── ApiErrorResponse.java         unified error envelope
│   │   │   │   ├── CreateMessageRequest.java
│   │   │   │   ├── CreateSessionRequest.java
│   │   │   │   ├── HealthResponse.java
│   │   │   │   ├── MessageResponse.java
│   │   │   │   ├── PageResponse.java             generic paginated envelope
│   │   │   │   ├── SessionResponse.java
│   │   │   │   └── UpdateSessionRequest.java
│   │   │   ├── security/
│   │   │   │   ├── ApiKeyAuthenticationFilter.java  constant-time API key validation
│   │   │   │   ├── RateLimitDecision.java           immutable rate-limit result record
│   │   │   │   ├── RateLimitFilter.java             429 enforcement + response headers
│   │   │   │   ├── RateLimitService.java            fixed-window token bucket
│   │   │   │   └── SecurityConfig.java              Spring Security filter chain
│   │   │   ├── config/
│   │   │   │   ├── CorsProperties.java              app.cors.*
│   │   │   │   ├── LoggingConfig.java               tracing filter registration
│   │   │   │   ├── OpenApiConfig.java               Swagger metadata + API key scheme
│   │   │   │   ├── RateLimitProperties.java         app.rate-limit.*
│   │   │   │   ├── RequestTraceContext.java          MDC + request attribute helpers
│   │   │   │   ├── RequestTracingFilter.java         per-request trace initialisation
│   │   │   │   ├── SecurityProperties.java           app.security.*
│   │   │   │   └── WebConfig.java                   CORS configuration source
│   │   │   └── exception/
│   │   │       ├── BadRequestException.java         → HTTP 400
│   │   │       ├── GlobalExceptionHandler.java      maps all exceptions → ApiErrorResponse
│   │   │       └── ResourceNotFoundException.java   → HTTP 404
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-e2e.properties
│   │       ├── db/migration/
│   │       │   └── V1__create_chat_tables.sql
│   │       └── static/
│   │           ├── index.html
│   │           ├── app.css
│   │           └── app.js
│   └── test/
│       └── java/ai/xdigit/ragchatstorage/   (182 unit + integration tests)
│           ├── config/
│           ├── dto/
│           ├── exception/
│           ├── integration/
│           ├── model/
│           ├── security/
│           └── service/
├── e2e/
│   └── rag-chat.spec.ts                     33 Playwright E2E tests
├── Dockerfile                               multi-stage build (Maven → JRE Alpine)
├── docker-compose.yml                       app + named H2 volume, all env defaults inline
├── playwright.config.ts
├── package.json
├── .env.example                             all environment variables documented
├── .gitignore
└── RAG_Chat_Storage.postman_collection.json 87-request Postman collection
```

---

## Example curl Commands

### Create a session

```bash
curl -s -X POST http://localhost:8080/api/v1/sessions \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me' \
  -d '{"userId":"user-123","title":"My first chat"}' | jq .
```

### List sessions (all)

```bash
curl -s 'http://localhost:8080/api/v1/sessions?userId=user-123&page=0&size=20' \
  -H 'X-API-Key: change-me' | jq .
```

### List starred sessions only

```bash
curl -s 'http://localhost:8080/api/v1/sessions?userId=user-123&favorite=true' \
  -H 'X-API-Key: change-me' | jq .
```

### Add a USER message

```bash
SESSION_ID=<paste-id-from-create>

curl -s -X POST "http://localhost:8080/api/v1/sessions/${SESSION_ID}/messages?userId=user-123" \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me' \
  -d '{"sender":"USER","content":"What did the retriever return?"}' | jq .
```

### Add an ASSISTANT message with RAG context

```bash
curl -s -X POST "http://localhost:8080/api/v1/sessions/${SESSION_ID}/messages?userId=user-123" \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me' \
  -d '{
    "sender": "ASSISTANT",
    "content": "Based on the retrieved documents, the answer is...",
    "retrievedContext": "{\"source\":\"kb-1\",\"chunks\":2,\"score\":0.91}"
  }' | jq .
```

### Rename a session

```bash
curl -s -X PATCH "http://localhost:8080/api/v1/sessions/${SESSION_ID}?userId=user-123" \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me' \
  -d '{"title":"Renamed chat"}' | jq .
```

### Star a session

```bash
curl -s -X PATCH "http://localhost:8080/api/v1/sessions/${SESSION_ID}?userId=user-123" \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me' \
  -d '{"favorite":true}' | jq .
```

### Delete a session (cascades to messages)

```bash
curl -s -X DELETE "http://localhost:8080/api/v1/sessions/${SESSION_ID}?userId=user-123" \
  -H 'X-API-Key: change-me'
# HTTP 204 No Content
```

### Check rate limit headers

```bash
curl -sI "http://localhost:8080/api/v1/sessions?userId=user-123" \
  -H 'X-API-Key: change-me' | grep -i 'x-rate-limit'
# X-Rate-Limit-Limit: 60
# X-Rate-Limit-Remaining: 59
```
