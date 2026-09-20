CREATE TABLE two_factor_auth (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    secret_encrypted TEXT NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE two_factor_recovery_codes (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    two_factor_auth_id UUID NOT NULL REFERENCES two_factor_auth(id) ON DELETE CASCADE,
    code_hash          VARCHAR(255) NOT NULL,
    used_at            TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_two_factor_recovery_codes_auth_id ON two_factor_recovery_codes (two_factor_auth_id);
