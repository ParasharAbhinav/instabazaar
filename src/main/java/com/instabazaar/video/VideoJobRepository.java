package com.instabazaar.video;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface VideoJobRepository extends JpaRepository<VideoJob, Long> {
    boolean existsByOwnerIdAndStatusIn(Long ownerId, Collection<VideoJob.Status> statuses);
    long countByOwnerIdAndCreatedAtAfter(Long ownerId, Instant since);
    List<VideoJob> findByStatusIn(Collection<VideoJob.Status> statuses);
    List<VideoJob> findTop10ByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from VideoJob job where job.id = :id")
    Optional<VideoJob> lockById(Long id);
}
