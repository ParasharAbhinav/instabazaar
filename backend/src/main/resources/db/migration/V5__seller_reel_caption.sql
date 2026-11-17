-- Optional seller-only text. Public DTOs continue to expose the source link,
-- description and approved tags, without exposing the working caption.
ALTER TABLE product ADD COLUMN reel_caption VARCHAR(2000);
