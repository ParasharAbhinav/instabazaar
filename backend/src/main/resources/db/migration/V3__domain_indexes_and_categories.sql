CREATE INDEX ix_user_role_enabled ON app_user(role, enabled, created_at DESC, id);
CREATE UNIQUE INDEX uq_category_name_ci ON category(lower(name));
CREATE INDEX ix_media_owner ON media_asset(owner_user_id, created_at DESC, id);
CREATE INDEX ix_store_logo ON seller_profile(logo_asset_id);
CREATE INDEX ix_product_seller ON product(seller_id, listing_status, updated_at DESC, id);
CREATE INDEX ix_product_category ON product(category_id);
CREATE INDEX ix_product_search ON product USING GIN(search_document);
CREATE INDEX ix_public_product_newest ON product(created_at DESC, id) WHERE listing_status='ACTIVE' AND moderation_status='VISIBLE';
CREATE INDEX ix_public_product_price ON product(price, id) WHERE listing_status='ACTIVE' AND moderation_status='VISIBLE';
CREATE INDEX ix_public_product_category_price ON product(category_id, price, id) WHERE listing_status='ACTIVE' AND moderation_status='VISIBLE';
CREATE INDEX ix_public_product_colour_price ON product(colour, price, id) WHERE listing_status='ACTIVE' AND moderation_status='VISIBLE';
CREATE INDEX ix_size_code ON product_size(size_code, product_id);
CREATE INDEX ix_approved_tag ON product_tag(normalized_tag, product_id) WHERE status='APPROVED';
CREATE INDEX ix_wishlist_user_created ON wishlist_item(user_id, created_at DESC, product_id);
CREATE INDEX ix_wishlist_product ON wishlist_item(product_id);
CREATE INDEX ix_approved_review ON review(product_id, created_at DESC, id) WHERE status='APPROVED';
CREATE INDEX ix_review_status ON review(status, created_at, id);
CREATE INDEX ix_review_reviewer ON review(reviewer_id);
CREATE UNIQUE INDEX uq_open_product_report ON report(reporter_id, product_id) WHERE status='OPEN' AND product_id IS NOT NULL;
CREATE UNIQUE INDEX uq_open_review_report ON report(reporter_id, review_id) WHERE status='OPEN' AND review_id IS NOT NULL;
CREATE INDEX ix_report_status ON report(status, created_at, id);
CREATE INDEX ix_report_product ON report(product_id);
CREATE INDEX ix_report_review ON report(review_id);
CREATE INDEX ix_report_resolver ON report(resolved_by);
CREATE INDEX ix_audit_target ON admin_audit_event(target_type, target_id, created_at DESC);
CREATE INDEX ix_audit_actor ON admin_audit_event(actor_id, created_at DESC);

INSERT INTO category(id, name, slug) VALUES
('00000000-0000-4000-8000-000000000001', 'Dresses', 'dresses'),
('00000000-0000-4000-8000-000000000002', 'Kurtis', 'kurtis'),
('00000000-0000-4000-8000-000000000003', 'Ethnic Wear', 'ethnic-wear'),
('00000000-0000-4000-8000-000000000004', 'Casual Wear', 'casual-wear'),
('00000000-0000-4000-8000-000000000005', 'Tops', 'tops'),
('00000000-0000-4000-8000-000000000006', 'Other Garments', 'other-garments');

