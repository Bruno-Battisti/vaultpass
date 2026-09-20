CREATE TABLE refresh_tokens (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash            VARCHAR(255) NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    revoked               BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at            TIMESTAMPTZ,
    replaced_by_token_id  UUID REFERENCES refresh_tokens(id),
    created_by_ip         VARCHAR(45),
    user_agent            VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
