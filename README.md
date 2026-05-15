# RAG Chat Storage Service

A production-ready Spring Boot microservice for persisting chat sessions and messages in Retrieval-Augmented Generation (RAG) applications. Ships with a built-in browser UI, API-key authentication, fixed-window rate limiting, structured request tracing, Flyway schema migrations, Swagger UI, and container support via Docker Compose or Podman Compose.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Prerequisites](#prerequisites)
- [Quick Start — Local Maven](#quick-start--local-maven)
- [Quick Start — Docker Compose](#quick-start--docker-compose)
- [Quick Start — Podman Compose](#quick-start--podman-compose)
- [Configuration Reference](#configuration-reference)
- [API Reference](#api-reference)
- [Request / Response Contracts](#request--response-contracts)
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
│  SessionController   /api/v1/sessions                           │
│  MessageController   /api/v1/sessions/{id}/messages             │
│  HealthController    /api/v1/health                             │
│                                                                 │
│  SessionService  ──  MessageService  ──  HealthService          │
│                                                                 │
│  Spring Data JPA  ──  Flyway migrations                         │
│                                                                 │
│  H2 (file-backed, PostgreSQL-compat mode)                       │
│  ./data/rag-chat.mv.db                                          │
└─────────────────────────────────────────────────────────────────┘
```

**Stack:** Java 21 · Spring Boot 3.3.5 · Spring Security · Spring Data JPA · H2 · Flyway · SpringDoc OpenAPI 2.6.0

---

## Prerequisites

| Tool | Minimum version | Notes |
|---|---|---|
| JDK | 21 | Temurin, Corretto, or any OpenJDK 21 distribution |
| Maven | 3.9+ | Only needed for the local Maven path |
| Docker **or** Podman | Latest stable | Only needed for the container path |
| Node.js | 18+ | Only needed to run Playwright E2E tests |

---

## Quick Start — Local Maven

### 1. Clone and enter the project

```bash
git clone <repo-url> rag-chat-storage-service
cd rag-chat-storage-service
```

### 2. Set the API key (and any other overrides)

The simplest approach is to export environment variables before running:

```bash
export API_KEY=my-secret-key
```

Or create a `.env` file and source it:

```bash
cp .env.example .env        # edit .env to set at least API_KEY
export $(grep -v '^#' .env | xargs)
```

### 3. Start the application

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080` by default.

### 4. Verify it is running

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP", ...}
```

```bash
curl -H 'X-API-Key: my-secret-key' http://localhost:8080/api/v1/health
# {"status":"UP","liveness":"UP","readiness":"UP","timestamp":"..."}
```

---

## Quick Start — Docker Compose

### 1. Create a `.env` file

```bash
cd rag-chat-storage-service
cp .env.example .env
```

Minimum `.env` content:

```dotenv
API_KEY=my-secret-key
APP_PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:8080
```

### 2. Build and start

```bash
docker compose up --build
```

> **Background mode:** append `-d` to run detached.

The compose file mounts a named volume (`h2-data`) to `/app/data` inside the container so the H2 database file persists across container restarts.

### 3. Verify it is running

```bash
curl http://localhost:8080/actuator/health
```

### 4. Stop and clean up

```bash
docker compose down          # stops containers, keeps the named volume
docker compose down -v       # stops containers AND removes the named volume (data loss!)
```

---

## Quick Start — Podman Compose

> **Prerequisite:** Podman Desktop must be installed and the Podman machine must be running.
> Verify with: `podman machine list`

> **One-time credential fix:** If you see the error below, remove the `"credsStore"` key from `~/.docker/config.json`:
> ```
> error listing credentials — exec: "docker-credential-desktop": executable file not found in $PATH
> ```
> Open `~/.docker/config.json` and delete the line `"credsStore": "desktop"`, then save.

### 1. Create a `.env` file

```bash
cd rag-chat-storage-service
cp .env.example .env
# Edit .env — at minimum, change API_KEY
```

Minimum `.env` content:

```dotenv
API_KEY=my-secret-key
APP_PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:8080
```

### 2. Build and start

```bash
podman compose up --build
```

> **Background mode:** append `-d` to run detached.

> **Note on first build time:** Step 4 of the Dockerfile (`RUN mvn dependency:go-offline`) downloads
> all ~200 Maven dependencies into the container image. This step is **completely silent** (Maven
> runs with `-q`) and takes **3–5 minutes** on the first build. Subsequent builds use the layer
> cache and complete in seconds. Your terminal is not frozen — just wait.

The compose file mounts a named volume (`h2-data`) to `/app/data` inside the container so the H2 database file persists across container restarts.

### 3. Verify it is running

```bash
curl http://localhost:8080/actuator/health
```

### 4. Stop and clean up

```bash
podman compose down          # stops containers, keeps the named volume
podman compose down -v       # stops containers AND removes the named volume (data loss!)
```

### Useful Podman commands

```bash
podman ps                                    # list running containers
podman logs -f rag-chat-storage-service      # tail application logs
podman exec -it rag-chat-storage-service sh  # open a shell inside the container
podman stats                                 # live CPU / memory usage per container
podman machine list                          # check machine status
```

---

## Configuration Reference

All settings have sensible defaults for local development. Override them via environment variables or by editing `src/main/resources/application.properties`.

| Environment variable | Property | Default | Description |
|---|---|---|---|
| `APP_PORT` | `server.port` | `8080` | HTTP listen port |
| `DB_PATH` | `spring.datasource.url` (path segment) | `./data/rag-chat` | H2 file path without extension |
| `DB_USERNAME` | `spring.datasource.username` | `sa` | H2 JDBC username |
| `DB_PASSWORD` | `spring.datasource.password` | *(empty)* | H2 JDBC password |
| `H2_CONSOLE_ENABLED` | `spring.h2.console.enabled` | `true` | Enable the H2 web console at `/h2-console` |
| `API_KEY_HEADER` | `app.security.api-key-header` | `X-API-Key` | Name of the authentication header |
| `API_KEY` | `app.security.api-key` | `change-me` | **Required in production** — the secret API key |
| `CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | `http://localhost:3000` | Comma-separated list of allowed CORS origins |
| `RATE_LIMIT_CAPACITY` | `app.rate-limit.capacity` | `60` | Max requests per window per API key |
| `RATE_LIMIT_WINDOW` | `app.rate-limit.window` | `PT1M` | Rate-limit window as an ISO-8601 duration |

### `.env.example`

```dotenv
APP_PORT=8080

DB_PATH=./data/rag-chat
DB_USERNAME=sa
DB_PASSWORD=
H2_CONSOLE_ENABLED=true

API_KEY_HEADER=X-API-Key
API_KEY=change-me

RATE_LIMIT_CAPACITY=60
RATE_LIMIT_WINDOW=PT1M

CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080
```

> **Production checklist**
> - Set `API_KEY` to a long random secret (not `change-me`).
> - Set `CORS_ALLOWED_ORIGINS` to your actual frontend origin(s).
> - Set `H2_CONSOLE_ENABLED=false` (the console is useful only for local debugging).

---

## API Reference

All `/api/v1/**` endpoints require the configured API key in the `X-API-Key` header (or the header name set by `API_KEY_HEADER`).

### Sessions

| Method | Path | Status | Description |
|---|---|---|---|
| `POST` | `/api/v1/sessions` | 201 | Create a new chat session |
| `GET` | `/api/v1/sessions` | 200 | List sessions for a user (paginated) |
| `PATCH` | `/api/v1/sessions/{sessionId}` | 200 | Update title and/or favorite flag |
| `DELETE` | `/api/v1/sessions/{sessionId}` | 204 | Delete a session and all its messages |

**Query parameters for `GET /api/v1/sessions`:**

| Parameter | Type | Required | Default | Constraint |
|---|---|---|---|---|
| `userId` | string | yes | — | max 100 chars |
| `favorite` | boolean | no | *(all)* | `true` or `false` |
| `page` | integer | no | `0` | ≥ 0 |
| `size` | integer | no | `20` | 1–100 |

**Query parameters for `PATCH` and `DELETE`:**

| Parameter | Type | Required |
|---|---|---|
| `userId` | string | yes |

### Messages

| Method | Path | Status | Description |
|---|---|---|---|
| `POST` | `/api/v1/sessions/{sessionId}/messages` | 201 | Append a message to a session |
| `GET` | `/api/v1/sessions/{sessionId}/messages` | 200 | Get paginated message history |

**Query parameters (both message endpoints):**

| Parameter | Type | Required | Default | Constraint |
|---|---|---|---|---|
| `userId` | string | yes | — | max 100 chars |
| `page` | integer | no | `0` | ≥ 0 (GET only) |
| `size` | integer | no | `20` | 1–100 (GET only) |

### Health

| Method | Path | Auth | Status | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/health` | yes | 200 / 503 | App-level readiness + liveness |
| `GET` | `/actuator/health` | no | 200 / 503 | Spring Actuator health (Kubernetes probes) |
| `GET` | `/actuator/info` | no | 200 | Build info |

---

## Request / Response Contracts

### Create session — `POST /api/v1/sessions`

```json
{
  "userId": "user-123",
  "title": "Research notes"
}
```

- `userId` is required (max 100 chars).
- `title` is optional; blank or absent values default to `"Untitled Chat"` (max 255 chars).

Response `201 Created`:

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "userId": "user-123",
  "title": "Research notes",
  "favorite": false,
  "messageCount": 0,
  "createdAt": "2026-05-15T10:00:00Z",
  "updatedAt": "2026-05-15T10:00:00Z"
}
```

### Update session — `PATCH /api/v1/sessions/{sessionId}?userId=user-123`

```json
{
  "title": "Renamed chat",
  "favorite": true
}
```

- At least one field must be present.
- `title`, if provided, must not be blank.

### Create message — `POST /api/v1/sessions/{sessionId}/messages?userId=user-123`

```json
{
  "sender": "ASSISTANT",
  "content": "Here is the answer based on the retrieved documents.",
  "retrievedContext": "{\"source\":\"kb-2\",\"chunks\":3}"
}
```

- `sender` must be `"USER"` or `"ASSISTANT"`.
- `content` is required (max 10 000 chars).
- `retrievedContext` is optional (max 20 000 chars); blank values are stored as `null`.

Response `201 Created`:

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "sender": "ASSISTANT",
  "content": "Here is the answer based on the retrieved documents.",
  "retrievedContext": "{\"source\":\"kb-2\",\"chunks\":3}",
  "createdAt": "2026-05-15T10:01:00Z"
}
```

### List sessions response — `GET /api/v1/sessions?userId=user-123`

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1,
  "hasNext": false
}
```

---

## Error Responses

All errors use a consistent JSON envelope:

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "Session 3fa8... was not found for user user-123",
  "timestamp": "2026-05-15T10:00:00Z",
  "path": "/api/v1/sessions/3fa8..."
}
```

| HTTP status | `code` | Cause |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation failure on request body fields |
| 400 | `BAD_REQUEST` | Business rule violated (e.g., empty update body, blank title) |
| 400 | `INVALID_REQUEST_BODY` | Malformed JSON or invalid enum value (e.g., unknown sender) |
| 400 | `INVALID_PARAMETER` | Bean Validation failure on a query or path parameter |
| 400 | `DATA_INTEGRITY_ERROR` | Database constraint violation (e.g., duplicate key) |
| 401 | `UNAUTHORIZED` | Missing or invalid `X-API-Key` header |
| 404 | `RESOURCE_NOT_FOUND` | Session not found or owned by a different user |
| 429 | `RATE_LIMIT_EXCEEDED` | Per-key request quota exhausted; check `Retry-After` header |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected server error |

---

## Security

Authentication uses a static API key compared in constant time (via `MessageDigest.isEqual`) to prevent timing attacks. Supply the key in the request header:

```
X-API-Key: my-secret-key
```

Routes that are publicly accessible without an API key:

- `GET /` and `/index.html` — the browser UI
- Static assets (`*.css`, `*.js`, `*.ico`)
- `GET /swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`
- `GET /actuator/health/**`, `/actuator/info/**`
- `GET /h2-console/**`
- `OPTIONS /**` (CORS preflight)

All `/api/v1/**` routes are protected.

---

## Rate Limiting

A fixed sliding-window rate limiter is applied per API key after authentication. Default limits: **60 requests per minute**.

Response headers on every allowed request:

| Header | Description |
|---|---|
| `X-Rate-Limit-Limit` | Maximum requests per window |
| `X-Rate-Limit-Remaining` | Requests remaining in the current window |

When the quota is exhausted, the service returns `HTTP 429` with:

| Header | Description |
|---|---|
| `Retry-After` | Seconds until the window resets |

Override defaults via environment variables:

```dotenv
RATE_LIMIT_CAPACITY=120
RATE_LIMIT_WINDOW=PT2M
```

---

## Browser UI

Open `http://localhost:8080/` in a browser.

The single-page UI lets you:

- **Login page** — enter your User ID and API key; the app validates them against the live service before proceeding.
- **Dark / Light theme** — toggle button in the top-right corner, persisted to `localStorage` across sessions.
- Enter your **userId** in the credentials bar at the top after login.
- **Create**, **rename**, **favorite**, and **delete** sessions from the left sidebar.
- Filter the session list by **All** / **Starred**.
- Select a session to view its **message history**.
- Compose and post `USER` and `ASSISTANT` messages, with an optional **RAG context** field.
- Click the **RAG context badge** on any message to inspect its retrieved chunks in the right panel.
- Monitor the live **health indicator** (polls every 30 s).
- Keyboard shortcuts: `N` new session · `Enter` send · `Shift+Enter` newline · `⌘K` clear view.

All data requests made by the UI include the `X-API-Key` header automatically.

---

## Swagger UI

Interactive API documentation is available without authentication:

```
http://localhost:8080/swagger-ui.html
```

Click the **Authorize** button and enter your API key to make authenticated test calls directly from the browser.

The raw OpenAPI spec (JSON):

```
http://localhost:8080/v3/api-docs
```

---

## H2 Console

The embedded H2 web console is enabled by default for local development.

```
http://localhost:8080/h2-console
```

| Run mode | JDBC URL |
|---|---|
| Local Maven | `jdbc:h2:file:./data/rag-chat` |
| Docker / Podman Compose | `jdbc:h2:file:/app/data/rag-chat` |

Username: `sa` · Password: *(leave blank)*

> Disable the console in production by setting `H2_CONSOLE_ENABLED=false`.

---

## Actuator Endpoints

Spring Boot Actuator exposes health probes without requiring an API key:

| Endpoint | Use |
|---|---|
| `GET /actuator/health` | Full health detail (liveness + readiness) |
| `GET /actuator/health/liveness` | Kubernetes liveness probe |
| `GET /actuator/health/readiness` | Kubernetes readiness probe |
| `GET /actuator/info` | Application metadata |

---

## Request Tracing

Every request receives two tracing headers echoed in the response:

| Header | Behaviour |
|---|---|
| `X-Request-Id` | Generated as a random UUID if not supplied by the caller |
| `X-Correlation-Id` | Taken from the caller's header; falls back to `X-Request-Id` |

Both IDs are included in every log line via SLF4J MDC:

```
2026-05-15T10:00:00.000Z [http-nio-8080-exec-1] INFO  ... [req=abc-123 corr=xyz-456 path=/api/v1/sessions] - request.completed method=GET status=200 durationMs=4
```

Pass `X-Correlation-Id` from your gateway or client to correlate a chain of service calls in distributed logs.

---

## Running Tests

### Unit + Integration Tests (JUnit 5)

```bash
mvn test
```

Tests use an in-memory H2 instance. No external dependencies are required. The suite contains **182 tests** covering unit, service, filter, security, and full Spring Boot integration layers.

To run a specific test class:

```bash
mvn test -Dtest=SessionServiceExtendedTest
```

To run a specific test method:

```bash
mvn test -Dtest=GlobalExceptionHandlerTest#handleUnexpectedReturns500WithInternalServerErrorCode
```

---

## E2E Tests (Playwright)

A Playwright test suite covering **33 end-to-end UI scenarios** is included in the `e2e/` directory. It tests every user-facing feature: login flow, theme toggle, session CRUD, messaging, RAG context panel, keyboard shortcuts, pagination, starred filter, inline rename, logout, and more.

### Prerequisites

- Node.js 18+
- The Spring Boot application must be running on port 8080

### Setup (first time only)

```bash
cd rag-chat-storage-service
npm install
npx playwright install chromium
```

### Run against local Maven server

```bash
# Terminal 1 — start the app
mvn spring-boot:run

# Terminal 2 — run the tests
npx playwright test --project=chromium
```

### Run against Docker / Podman Compose

```bash
# Start the container in background
docker compose up --build -d
# or
podman compose up --build -d

# Run the tests
npx playwright test --project=chromium
```

### Run all browsers (Chromium, Firefox, WebKit)

```bash
npx playwright test
```

### View the HTML report

```bash
npx playwright show-report
```

### Run in headed mode (visible browser)

```bash
npx playwright test --headed --project=chromium
```

---

## Postman Collection

A comprehensive Postman collection with 87 requests is included at the project root:

```
RAG_Chat_Storage.postman_collection.json
```

Import it into Postman (File → Import), then set the collection variables:

| Variable | Default | Description |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Base URL of the running service |
| `apiKey` | `change-me` | Must match `API_KEY` in your configuration |
| `userId` | `postman-user` | Owner ID used in all test requests |

The collection covers:

| Folder | What it tests |
|---|---|
| 00 · Health | Liveness and auth checks |
| 01 · Sessions Create | Happy path, title defaults, boundary validation, auth failures |
| 02 · Sessions List | Pagination, favorite filter, edge cases |
| 03 · Sessions Update | Partial updates, ownership checks |
| 04 · Messages Create | Both sender types, context handling, validation boundaries |
| 05 · Messages List | Pagination, ordering |
| 06 · Sessions Delete | Cascade verification, 404 on double-delete |
| 07 · Security | Timing-safe key checks, injection in userId, XSS storage |
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
│   │   │   ├── RagChatStorageApplication.java       # Entry point
│   │   │   ├── controller/
│   │   │   │   ├── HealthController.java            # GET /api/v1/health
│   │   │   │   ├── MessageController.java           # POST/GET /api/v1/sessions/{id}/messages
│   │   │   │   └── SessionController.java           # CRUD /api/v1/sessions
│   │   │   ├── service/
│   │   │   │   ├── HealthService.java               # Database probe
│   │   │   │   ├── MessageService.java              # Message business logic
│   │   │   │   └── SessionService.java              # Session business logic
│   │   │   ├── repository/
│   │   │   │   ├── ChatMessageRepository.java       # JPA message queries
│   │   │   │   ├── ChatSessionRepository.java       # JPA session queries
│   │   │   │   └── MessageCountView.java            # Batch count projection
│   │   │   ├── model/
│   │   │   │   ├── ChatMessage.java                 # Message entity
│   │   │   │   ├── ChatSession.java                 # Session entity (cascades to messages)
│   │   │   │   └── Sender.java                      # USER / ASSISTANT enum
│   │   │   ├── dto/
│   │   │   │   ├── ApiErrorResponse.java            # Unified error envelope
│   │   │   │   ├── CreateMessageRequest.java
│   │   │   │   ├── CreateSessionRequest.java
│   │   │   │   ├── HealthResponse.java
│   │   │   │   ├── MessageResponse.java
│   │   │   │   ├── PageResponse.java
│   │   │   │   ├── SessionResponse.java
│   │   │   │   └── UpdateSessionRequest.java
│   │   │   ├── security/
│   │   │   │   ├── ApiKeyAuthenticationFilter.java  # Constant-time API key validation
│   │   │   │   ├── RateLimitDecision.java           # Immutable rate-limit result
│   │   │   │   ├── RateLimitFilter.java             # 429 enforcement
│   │   │   │   ├── RateLimitService.java            # Fixed-window token bucket
│   │   │   │   └── SecurityConfig.java              # Spring Security filter chain
│   │   │   ├── config/
│   │   │   │   ├── CorsProperties.java              # app.cors.*
│   │   │   │   ├── LoggingConfig.java               # Tracing filter registration
│   │   │   │   ├── OpenApiConfig.java               # Swagger/OpenAPI metadata
│   │   │   │   ├── RateLimitProperties.java         # app.rate-limit.*
│   │   │   │   ├── RequestTraceContext.java         # MDC + request attribute helpers
│   │   │   │   ├── RequestTracingFilter.java        # Per-request trace initialisation
│   │   │   │   ├── SecurityProperties.java          # app.security.*
│   │   │   │   └── WebConfig.java                   # CORS configuration source
│   │   │   └── exception/
│   │   │       ├── BadRequestException.java         # → HTTP 400
│   │   │       ├── GlobalExceptionHandler.java      # Maps all exceptions to ApiErrorResponse
│   │   │       └── ResourceNotFoundException.java   # → HTTP 404
│   │   └── resources/
│   │       ├── application.properties               # Runtime defaults + env var bindings
│   │       ├── db/migration/
│   │       │   └── V1__create_chat_tables.sql       # Flyway baseline schema
│   │       └── static/
│   │           └── index.html                       # Single-file browser UI (login + app)
│   └── test/
│       └── java/ai/xdigit/ragchatstorage/           # 182 unit + integration tests
│           ├── config/
│           ├── dto/
│           ├── exception/
│           ├── integration/
│           ├── model/
│           ├── security/
│           └── service/
├── e2e/
│   └── rag-chat.spec.ts                             # 33 Playwright E2E tests
├── Dockerfile                                       # Multi-stage build (Maven → JRE)
├── docker-compose.yml                               # App + named H2 volume
├── playwright.config.ts                             # Playwright configuration
├── package.json                                     # Node dependencies for E2E tests
├── .env.example                                     # Environment variable template
└── RAG_Chat_Storage.postman_collection.json         # 87-request Postman collection
```

---

## Example curl Commands

**Create a session:**

```bash
curl -s -X POST http://localhost:8080/api/v1/sessions \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me' \
  -d '{"userId":"user-123","title":"My first chat"}' | jq .
```

**List sessions:**

```bash
curl -s 'http://localhost:8080/api/v1/sessions?userId=user-123&page=0&size=20' \
  -H 'X-API-Key: change-me' | jq .
```

**List only starred sessions:**

```bash
curl -s 'http://localhost:8080/api/v1/sessions?userId=user-123&favorite=true' \
  -H 'X-API-Key: change-me' | jq .
```

**Append a USER message:**

```bash
SESSION_ID=<paste-id-from-create-response>

curl -s -X POST "http://localhost:8080/api/v1/sessions/${SESSION_ID}/messages?userId=user-123" \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me' \
  -d '{"sender":"USER","content":"What did the retriever return?"}' | jq .
```

**Append an ASSISTANT message with RAG context:**

```bash
curl -s -X POST "http://localhost:8080/api/v1/sessions/${SESSION_ID}/messages?userId=user-123" \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me' \
  -d '{
    "sender": "ASSISTANT",
    "content": "Based on the retrieved documents, the answer is ...",
    "retrievedContext": "{\"source\":\"kb-1\",\"chunks\":2}"
  }' | jq .
```

**Star a session:**

```bash
curl -s -X PATCH "http://localhost:8080/api/v1/sessions/${SESSION_ID}?userId=user-123" \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me' \
  -d '{"favorite":true}' | jq .
```

**Rename a session:**

```bash
curl -s -X PATCH "http://localhost:8080/api/v1/sessions/${SESSION_ID}?userId=user-123" \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me' \
  -d '{"title":"Renamed chat"}' | jq .
```

**Delete a session (cascades to messages):**

```bash
curl -s -X DELETE "http://localhost:8080/api/v1/sessions/${SESSION_ID}?userId=user-123" \
  -H 'X-API-Key: change-me'
# HTTP 204 No Content
```

**Check rate limit headers:**

```bash
curl -sI "http://localhost:8080/api/v1/sessions?userId=user-123" \
  -H 'X-API-Key: change-me' | grep -i 'x-rate-limit'
# X-Rate-Limit-Limit: 60
# X-Rate-Limit-Remaining: 59
```
