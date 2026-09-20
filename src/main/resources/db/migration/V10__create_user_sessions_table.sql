CREATE TABLE user_sessions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_id UUID REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    ip_address       VARCHAR(45),
    user_agent       VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_user_sessions_user_id ON user_sessions (user_id);
