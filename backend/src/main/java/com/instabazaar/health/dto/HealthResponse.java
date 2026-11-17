package com.instabazaar.health.dto;

import java.time.Instant;

public record HealthResponse(String application, String status, Checks checks, Instant checkedAt) {
    public record Checks(String database, String migrations) {}
}
