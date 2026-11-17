package com.instabazaar.repository;

import com.instabazaar.entity.ProductImage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<ProductImage, Long> {
    Optional<ProductImage> findByStorageKey(String storageKey);
}
