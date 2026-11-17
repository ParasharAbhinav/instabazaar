package com.instabazaar.health.controller;

import com.instabazaar.health.dto.HealthResponse;
import com.instabazaar.health.service.HealthService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final HealthService service;

    public HealthController(HealthService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = service.check();
        return ResponseEntity.status("UP".equals(response.status()) ? 200 : 503)
                .cacheControl(CacheControl.noStore()).body(response);
    }
}
