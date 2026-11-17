-- Data-model extension. V1 is preserved byte for byte.

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(btrim(email))),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('BUYER', 'SELLER', 'ADMIN')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE category (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL CHECK (btrim(name) <> ''),
    slug VARCHAR(100) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE media_asset (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('image/jpeg','image/png','image/webp')),
    byte_size BIGINT NOT NULL CHECK (byte_size > 0 AND byte_size <= 5242880),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    state VARCHAR(10) NOT NULL DEFAULT 'READY' CHECK (state IN ('READY', 'DELETING')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE seller_profile (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE RESTRICT,
    store_name VARCHAR(120) NOT NULL CHECK (btrim(store_name) <> ''),
    description VARCHAR(2000) NOT NULL,
    logo_asset_id UUID REFERENCES media_asset(id) ON DELETE RESTRICT,
    public_email VARCHAR(254),
    public_phone VARCHAR(20),
    whatsapp_number VARCHAR(20),
    instagram_url VARCHAR(2048),
    facebook_url VARCHAR(2048),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES seller_profile(id) ON DELETE RESTRICT,
    category_id UUID NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
    title VARCHAR(160) NOT NULL CHECK (btrim(title) <> ''),
    description VARCHAR(5000) NOT NULL,
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
    colour VARCHAR(40) NOT NULL CHECK (colour = lower(btrim(colour)) AND colour <> ''),
    material VARCHAR(100),
    occasion VARCHAR(100),
    brand VARCHAR(100),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    available BOOLEAN NOT NULL DEFAULT TRUE,
    listing_status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (listing_status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    moderation_status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' CHECK (moderation_status IN ('VISIBLE', 'HIDDEN')),
    source_url VARCHAR(2048),
    search_document TSVECTOR NOT NULL DEFAULT ''::tsvector,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_image (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL UNIQUE REFERENCES media_asset(id) ON DELETE RESTRICT,
    display_order SMALLINT NOT NULL CHECK (display_order BETWEEN 0 AND 9),
    alt_text VARCHAR(160) NOT NULL,
    CONSTRAINT uq_product_image_order UNIQUE(product_id, display_order) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE product_tag (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    tag VARCHAR(60) NOT NULL CHECK (btrim(tag) <> ''),
    normalized_tag VARCHAR(60) NOT NULL CHECK (normalized_tag = lower(btrim(tag))),
    source VARCHAR(10) NOT NULL CHECK (source IN ('SELLER', 'SYSTEM')),
    status VARCHAR(10) NOT NULL CHECK (status IN ('SUGGESTED', 'APPROVED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_tag UNIQUE(product_id, normalized_tag)
);

CREATE TABLE review (
    id UUID PRIMARY KEY,
    reviewer_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text VARCHAR(2000),
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_review_author_product UNIQUE(reviewer_id, product_id)
);

CREATE TABLE report (
    id UUID PRIMARY KEY,
    reporter_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    product_id UUID REFERENCES product(id) ON DELETE RESTRICT,
    review_id UUID REFERENCES review(id) ON DELETE RESTRICT,
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('MISLEADING', 'INAPPROPRIATE', 'SPAM', 'OTHER')),
    details VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    resolution_note VARCHAR(1000),
    resolved_by UUID REFERENCES app_user(id) ON DELETE RESTRICT,
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_report_target CHECK ((product_id IS NOT NULL) <> (review_id IS NOT NULL)),
    CONSTRAINT ck_report_resolution CHECK ((status = 'OPEN' AND resolved_by IS NULL AND resolved_at IS NULL AND resolution_note IS NULL) OR (status IN ('RESOLVED','DISMISSED') AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL AND resolution_note IS NOT NULL AND btrim(resolution_note) <> ''))
);

CREATE TABLE admin_audit_event (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    action VARCHAR(40) NOT NULL CHECK (action IN ('ACCOUNT_STATUS', 'PRODUCT_MODERATION', 'REVIEW_MODERATION', 'REPORT_RESOLUTION')),
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('USER', 'PRODUCT', 'REVIEW', 'REPORT')),
    target_id UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL CHECK (btrim(reason) <> ''),
    before_state JSONB NOT NULL CHECK (jsonb_typeof(before_state) = 'object'),
    after_state JSONB NOT NULL CHECK (jsonb_typeof(after_state) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_size (
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    size_code VARCHAR(20) NOT NULL CHECK (size_code = upper(btrim(size_code)) AND size_code <> ''),
    PRIMARY KEY(product_id, size_code)
);

CREATE TABLE wishlist_item (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id, product_id)
);
