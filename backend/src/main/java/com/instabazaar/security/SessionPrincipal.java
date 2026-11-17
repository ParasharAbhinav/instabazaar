package com.instabazaar.security;

import java.io.Serializable;
import java.util.UUID;

/** Server-created identity only; never a request DTO. */
public record SessionPrincipal(UUID userId) implements Serializable {
  public SessionPrincipal {
    java.util.Objects.requireNonNull(userId);
  }
}
