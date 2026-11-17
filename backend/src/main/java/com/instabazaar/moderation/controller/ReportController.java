package com.instabazaar.moderation.controller;

import com.instabazaar.moderation.service.ReportService;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.web.Requests;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Contracts.Report create(@Valid @RequestBody Requests.Report body) {
    return reports.create(body.targetType(), body.targetId(), body.reason(), body.details());
  }
}
