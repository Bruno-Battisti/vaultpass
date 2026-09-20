CREATE TABLE credentials (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id             UUID NULL,
    title                   VARCHAR(150) NOT NULL,
    username                VARCHAR(255),
    encrypted_password      TEXT NOT NULL,
    encryption_key_version  SMALLINT NOT NULL DEFAULT 1,
    url                     VARCHAR(2048),
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_credentials_user_id ON credentials (user_id);
