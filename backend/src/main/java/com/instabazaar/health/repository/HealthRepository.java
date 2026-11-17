package com.instabazaar.health.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HealthRepository {
    private final JdbcTemplate jdbc;

    public HealthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean databaseResponds() {
        return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
    }

    public boolean foundationExists() {
        return "foundation".equals(jdbc.queryForObject(
                "SELECT phase FROM foundation_metadata WHERE id = 1", String.class));
    }
}
