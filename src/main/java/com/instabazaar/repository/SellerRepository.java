package com.instabazaar.repository;

import com.instabazaar.entity.SellerProfile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerRepository extends JpaRepository<SellerProfile, Long> {
    Optional<SellerProfile> findByUserId(Long userId);
    Optional<SellerProfile> findByLogoKey(String logoKey);
    Page<SellerProfile> findByUserActiveTrueAndStoreNameContainingIgnoreCase(String term, Pageable pageable);
}
