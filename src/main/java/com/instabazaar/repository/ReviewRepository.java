package com.instabazaar.repository;

import com.instabazaar.entity.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdAndHiddenFalseAndUserActiveTrueOrderByCreatedAtDesc(Long productId);
    List<Review> findByProductSellerIdAndHiddenFalseAndUserActiveTrue(Long sellerId);
    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);
}
