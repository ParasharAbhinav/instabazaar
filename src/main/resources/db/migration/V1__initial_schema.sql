CREATE TABLE app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL,
    display_name VARCHAR(80) NOT NULL, email VARCHAR(190) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL, active BOOLEAN NOT NULL, demo BOOLEAN NOT NULL,
    CONSTRAINT ck_user_role CHECK (role IN ('BUYER', 'SELLER', 'ADMIN'))
);
CREATE TABLE seller_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL,
    user_id BIGINT NOT NULL UNIQUE, store_name VARCHAR(100) NOT NULL, description VARCHAR(2000) NOT NULL,
    public_email VARCHAR(190), whatsapp_url VARCHAR(500), instagram_url VARCHAR(500), facebook_url VARCHAR(500), logo_key VARCHAR(100),
    CONSTRAINT fk_seller_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL, name VARCHAR(80) NOT NULL UNIQUE
);
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL,
    seller_id BIGINT NOT NULL, category_id BIGINT NOT NULL, title VARCHAR(150) NOT NULL, description VARCHAR(4000) NOT NULL,
    price DECIMAL(12,2) NOT NULL, colour VARCHAR(50) NOT NULL, sizes VARCHAR(120) NOT NULL,
    material VARCHAR(100), occasion VARCHAR(100), brand VARCHAR(100), stock INT NOT NULL,
    status VARCHAR(16) NOT NULL, hidden BOOLEAN NOT NULL, demo BOOLEAN NOT NULL,
    source_url VARCHAR(500), caption VARCHAR(2000), illustration VARCHAR(80),
    CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES seller_profiles(id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT ck_price CHECK (price > 0), CONSTRAINT ck_stock CHECK (stock >= 0),
    CONSTRAINT ck_listing_status CHECK (status IN ('ACTIVE', 'DRAFT', 'ARCHIVED'))
);
CREATE INDEX idx_product_catalogue ON products(status, hidden, category_id, price);
CREATE INDEX idx_product_seller ON products(seller_id);
CREATE TABLE product_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL,
    product_id BIGINT NOT NULL, label VARCHAR(50) NOT NULL, suggested BOOLEAN NOT NULL,
    CONSTRAINT uq_product_tag UNIQUE (product_id, label),
    CONSTRAINT fk_tag_product FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE product_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL,
    product_id BIGINT NOT NULL, storage_key VARCHAR(100) NOT NULL UNIQUE, content_type VARCHAR(40) NOT NULL,
    CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE wishlist_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL,
    user_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
    CONSTRAINT uq_wishlist UNIQUE (user_id, product_id),
    CONSTRAINT fk_wishlist_user FOREIGN KEY (user_id) REFERENCES app_users(id),
    CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL,
    user_id BIGINT NOT NULL, product_id BIGINT NOT NULL, rating INT NOT NULL, text VARCHAR(1500) NOT NULL, hidden BOOLEAN NOT NULL,
    CONSTRAINT uq_review UNIQUE (user_id, product_id), CONSTRAINT ck_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES app_users(id),
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE INDEX idx_review_product_visibility ON reviews(product_id, hidden);
