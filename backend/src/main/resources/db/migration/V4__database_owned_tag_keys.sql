-- Keep published V2 intact. PostgreSQL calculates the key using its own locale.
-- Dropping the column also removes its old unique constraint and tag index.
ALTER TABLE product_tag DROP COLUMN normalized_tag;
ALTER TABLE product_tag ADD COLUMN normalized_tag TEXT
    GENERATED ALWAYS AS (lower(btrim(tag))) STORED NOT NULL;
ALTER TABLE product_tag ADD CONSTRAINT uq_product_tag UNIQUE(product_id, normalized_tag);
CREATE INDEX ix_approved_tag ON product_tag(normalized_tag, product_id) WHERE status='APPROVED';
