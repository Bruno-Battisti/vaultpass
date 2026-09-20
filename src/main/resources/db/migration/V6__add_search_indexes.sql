CREATE INDEX ix_credentials_title_trgm ON credentials USING GIN (title gin_trgm_ops);
CREATE INDEX ix_credentials_username_trgm ON credentials USING GIN (username gin_trgm_ops);
