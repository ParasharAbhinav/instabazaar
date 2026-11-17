package com.instabazaar.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/** Masks request attributes while accepting the raw cookie token in the SPA header. */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
  public static final String RAW_TOKEN_ATTRIBUTE =
      SpaCsrfTokenRequestHandler.class.getName() + ".rawToken";
  private final XorCsrfTokenRequestAttributeHandler attributes =
      new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    attributes.handle(request, response, csrfToken);
    request.setAttribute(RAW_TOKEN_ATTRIBUTE, csrfToken.get());
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    // Header-only API: a multipart/form parameter must never substitute for this header.
    return request.getHeader(csrfToken.getHeaderName());
  }
}
