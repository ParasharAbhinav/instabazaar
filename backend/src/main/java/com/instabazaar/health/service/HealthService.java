package com.instabazaar.health.service;

import com.instabazaar.health.dto.HealthResponse;
import com.instabazaar.health.repository.HealthRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private final HealthRepository repository;

    public HealthService(HealthRepository repository) {
        this.repository = repository;
    }

    public HealthResponse check() {
        boolean database = false;
        boolean migrations = false;
        try {
            database = repository.databaseResponds();
            if (database) {
                migrations = repository.foundationExists();
            }
        } catch (DataAccessException failure) {
            // Health failures become 503 without exposing database connection details.
            log.warn("Foundation health check failed ({})", failure.getClass().getSimpleName());
        }
        return new HealthResponse("instabazaar", database && migrations ? "UP" : "DOWN",
                new HealthResponse.Checks(database ? "UP" : "DOWN", migrations ? "APPLIED" : "UNAVAILABLE"),
                Instant.now());
    }
}
