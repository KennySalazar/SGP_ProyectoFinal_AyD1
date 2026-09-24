ALTER TABLE token_refresco
ALTER COLUMN token_hash TYPE VARCHAR(64)
USING token_hash::VARCHAR(64);