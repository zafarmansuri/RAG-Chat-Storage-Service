CREATE TABLE chat_sessions (
    id UUID PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    sender VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    retrieved_context TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
