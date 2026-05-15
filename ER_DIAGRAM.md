# Entity Relationship Diagram

## Mermaid Diagram (renders on GitHub)

```mermaid
erDiagram
    CHAT_SESSIONS {
        UUID        id              PK  "NOT NULL"
        VARCHAR100  user_id             "NOT NULL"
        VARCHAR255  title               "NOT NULL"
        BOOLEAN     favorite            "NOT NULL DEFAULT FALSE"
        TIMESTAMPTZ created_at          "NOT NULL DEFAULT CURRENT_TIMESTAMP"
        TIMESTAMPTZ updated_at          "NOT NULL DEFAULT CURRENT_TIMESTAMP"
    }

    CHAT_MESSAGES {
        UUID        id              PK  "NOT NULL"
        UUID        session_id      FK  "NOT NULL"
        VARCHAR20   sender              "NOT NULL CHECK USER|ASSISTANT"
        TEXT        content             "NOT NULL"
        TEXT        retrieved_context   "NULL"
        TIMESTAMPTZ created_at          "NOT NULL DEFAULT CURRENT_TIMESTAMP"
    }

    CHAT_SESSIONS ||--o{ CHAT_MESSAGES : "has (CASCADE DELETE)"
```

---

## Detailed ASCII Diagram

```
╔═══════════════════════════════════════════════════════════════╗
║                       chat_sessions                           ║
╠══════════╦═══════════════╦══════════╦═════════════════════════╣
║ PK       ║ id            ║ UUID     ║ NOT NULL                ║
║          ║ user_id       ║ VARCHAR  ║ NOT NULL   max 100      ║
║          ║ title         ║ VARCHAR  ║ NOT NULL   max 255      ║
║          ║ favorite      ║ BOOLEAN  ║ NOT NULL   DEFAULT FALSE║
║          ║ created_at    ║ TIMESTAMPTZ NOT NULL DEFAULT NOW() ║
║          ║ updated_at    ║ TIMESTAMPTZ NOT NULL DEFAULT NOW() ║
╚══════════╩═══════════════╩══════════╩═════════════════════════╝
                           │
                           │ ONE
                           │
                           │  ON DELETE CASCADE
                           │
                           │ MANY
╔══════════════════════════▼════════════════════════════════════╗
║                       chat_messages                           ║
╠══════════╦═══════════════╦══════════╦═════════════════════════╣
║ PK       ║ id            ║ UUID     ║ NOT NULL                ║
║ FK ──────║ session_id    ║ UUID     ║ NOT NULL                ║
║          ║ sender        ║ VARCHAR  ║ NOT NULL   max 20       ║
║          ║               ║          ║ 'USER' | 'ASSISTANT'    ║
║          ║ content       ║ TEXT     ║ NOT NULL                ║
║          ║ retrieved_context TEXT   ║ NULL                    ║
║          ║ created_at    ║ TIMESTAMPTZ NOT NULL DEFAULT NOW() ║
╚══════════╩═══════════════╩══════════╩═════════════════════════╝
```

---

## Indexes

```
TABLE: chat_sessions
─────────────────────────────────────────────────────────────────
  PK   PRIMARY KEY (id)

  IDX  idx_chat_sessions_user_updated
       (user_id, updated_at DESC)
       → Used by: GET /api/v1/sessions  (list all sessions for a user)

  IDX  idx_chat_sessions_user_favorite_updated
       (user_id, favorite, updated_at DESC)
       → Used by: GET /api/v1/sessions?favorite=true|false

TABLE: chat_messages
─────────────────────────────────────────────────────────────────
  PK   PRIMARY KEY (id)

  FK   fk_chat_messages_session
       session_id → chat_sessions(id)
       ON DELETE CASCADE

  IDX  idx_chat_messages_session_created
       (session_id, created_at ASC)
       → Used by: GET /api/v1/sessions/{id}/messages  (ordered history)
```

---

## Constraints Summary

| Table | Constraint | Definition |
|---|---|---|
| `chat_sessions` | PK | `id` |
| `chat_sessions` | NOT NULL | `id`, `user_id`, `title`, `favorite`, `created_at`, `updated_at` |
| `chat_sessions` | DEFAULT | `favorite = FALSE`, `created_at = NOW()`, `updated_at = NOW()` |
| `chat_messages` | PK | `id` |
| `chat_messages` | FK | `session_id → chat_sessions(id) ON DELETE CASCADE` |
| `chat_messages` | NOT NULL | `id`, `session_id`, `sender`, `content`, `created_at` |
| `chat_messages` | NULLABLE | `retrieved_context` |
| `chat_messages` | CHECK (enum) | `sender IN ('USER', 'ASSISTANT')` enforced by JPA `@Enumerated(STRING)` |

---

## Relationships

```
chat_sessions 1 ─────────────────────── N chat_messages
              │                        │
              │  One session can       │  Each message belongs to
              │  have zero or many     │  exactly one session.
              │  messages.             │
              │                        │
              └── ON DELETE CASCADE ───┘
                  Deleting a session
                  deletes all its
                  messages automatically.
```

---

## Flyway Migration (V1)

The schema is managed by Flyway. The baseline migration lives at:

```
src/main/resources/db/migration/V1__create_chat_tables.sql
```

```sql
CREATE TABLE chat_sessions (
    id         UUID          PRIMARY KEY,
    user_id    VARCHAR(100)  NOT NULL,
    title      VARCHAR(255)  NOT NULL,
    favorite   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    id                UUID  PRIMARY KEY,
    session_id        UUID  NOT NULL,
    sender            VARCHAR(20) NOT NULL,
    content           TEXT        NOT NULL,
    retrieved_context TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_chat_sessions_user_updated
    ON chat_sessions (user_id, updated_at DESC);

CREATE INDEX idx_chat_sessions_user_favorite_updated
    ON chat_sessions (user_id, favorite, updated_at DESC);

CREATE INDEX idx_chat_messages_session_created
    ON chat_messages (session_id, created_at ASC);
```
