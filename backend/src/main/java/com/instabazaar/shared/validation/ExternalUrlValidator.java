package com.instabazaar.shared.validation;

import static com.instabazaar.shared.exception.BusinessException.invalid;

import java.net.URI;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ExternalUrlValidator {
  private static final Set<String> INSTAGRAM = Set.of("instagram.com", "www.instagram.com");
  private static final Set<String> FACEBOOK =
      Set.of("facebook.com", "www.facebook.com", "m.facebook.com");

  public String validate(String value, String field) {
    if (value == null) return null;
    Rules.text(value, field, 2048, false, true);
    try {
      URI uri = URI.create(value);
      String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
      boolean allowed =
          switch (field) {
            case "instagramUrl" -> INSTAGRAM.contains(host);
            case "facebookUrl" -> FACEBOOK.contains(host);
            default -> INSTAGRAM.contains(host) || FACEBOOK.contains(host);
          };
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || !allowed
          || uri.getRawUserInfo() != null
          || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException();
      return uri.toASCIIString();
    } catch (IllegalArgumentException ex) {
      throw invalid(field, "Use an HTTPS URL on an approved social host.");
    }
  }
}
