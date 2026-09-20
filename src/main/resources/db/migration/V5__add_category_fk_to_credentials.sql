ALTER TABLE credentials
    ADD CONSTRAINT fk_credentials_category
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;
