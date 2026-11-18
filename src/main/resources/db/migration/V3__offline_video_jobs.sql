CREATE TABLE video_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL,
    owner_id BIGINT NOT NULL, product_id BIGINT,
    status VARCHAR(16) NOT NULL, storage_key VARCHAR(36) NOT NULL,
    suggestions VARCHAR(8000) NOT NULL, approved_tags VARCHAR(1000) NOT NULL, message VARCHAR(300) NOT NULL,
    frame_count INT NOT NULL, duration DOUBLE NOT NULL,
    CONSTRAINT fk_video_owner FOREIGN KEY (owner_id) REFERENCES app_users(id),
    CONSTRAINT fk_video_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_video_status CHECK (status IN ('QUEUED','PROCESSING','READY','REVIEWED','APPLIED','FAILED'))
);
CREATE INDEX idx_video_owner_status ON video_jobs(owner_id, status, created_at);
