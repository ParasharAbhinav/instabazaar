package com.instabazaar.shared.exception;

import com.instabazaar.shared.dto.Contracts;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.*;
import org.springframework.stereotype.Component;

/** Reusable error contract for the later HTTP and security adapters; does not expose a route. */
@Component
public class ServiceErrorMapper {
  public record Failure(int status, Contracts.Error body) {}

  private final Clock clock;

  public ServiceErrorMapper(Clock clock) {
    this.clock = clock;
  }

  public Failure map(Throwable failure, String traceId) {
    String trace =
        traceId != null && traceId.matches("[A-Za-z0-9_-]{1,100}")
            ? traceId
            : UUID.randomUUID().toString();
    if (failure instanceof BusinessException business)
      return new Failure(
          business.getStatus(),
          new Contracts.Error(
              business.getCode(),
              business.getMessage(),
              business.getFieldErrors(),
              trace,
              clock.instant()));
    if (failure instanceof OptimisticLockingFailureException
        || failure instanceof jakarta.persistence.OptimisticLockException)
      return map(BusinessException.conflict(), trace);
    if (failure instanceof TransientDataAccessException
        || failure instanceof DataAccessResourceFailureException)
      return new Failure(
          503,
          new Contracts.Error(
              "SERVICE_UNAVAILABLE",
              "Service is temporarily unavailable.",
              List.of(),
              trace,
              clock.instant()));
    return new Failure(
        500,
        new Contracts.Error(
            "INTERNAL_ERROR", "An internal error occurred.", List.of(), trace, clock.instant()));
  }
}
