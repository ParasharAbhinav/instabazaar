package com.instabazaar.account.controller;

import com.instabazaar.account.service.AccountService;
import com.instabazaar.account.service.AuthenticationService;
import com.instabazaar.security.SpaCsrfTokenRequestHandler;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.web.Requests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthenticationService authentication;
  private final AccountService accounts;
  private final SecurityContextRepository contexts;
  private final SessionAuthenticationStrategy sessions;
  private final CookieCsrfTokenRepository csrf;
  private final boolean secureCookies;

  public AuthController(
      AuthenticationService authentication,
      AccountService accounts,
      SecurityContextRepository contexts,
      SessionAuthenticationStrategy sessions,
      CookieCsrfTokenRepository csrf,
      @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies) {
    this.authentication = authentication;
    this.accounts = accounts;
    this.contexts = contexts;
    this.sessions = sessions;
    this.csrf = csrf;
    this.secureCookies = secureCookies;
  }

  public record CsrfResponse(String headerName, String token) {}

  @GetMapping("/csrf")
  public CsrfResponse csrf(HttpServletRequest request, HttpServletResponse response) {
    // Return the raw repository token, not the XOR-masked request attribute.
    var token = (CsrfToken) request.getAttribute(SpaCsrfTokenRequestHandler.RAW_TOKEN_ATTRIBUTE);
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    return new CsrfResponse(token.getHeaderName(), token.getToken());
  }

  @PostMapping("/register")
  public ResponseEntity<Contracts.User> register(@Valid @RequestBody Requests.Register body) {
    var user = authentication.register(body.name(), body.email(), body.password(), body.role());
    return ResponseEntity.created(URI.create("/api/v1/me")).body(user);
  }

  @PostMapping("/login")
  public Contracts.User login(
      @Valid @RequestBody Requests.Login body,
      HttpServletRequest request,
      HttpServletResponse response) {
    var verified = authentication.verifyCredentials(body.email(), body.password());
    request.getSession(true);
    sessions.onAuthentication(verified, request, response);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(verified);
    SecurityContextHolder.setContext(context);
    try {
      var user = accounts.me();
      contexts.saveContext(context, request, response);
      // Authentication invalidates the previous CSRF cookie. Fetch /auth/csrf next.
      return user;
    } catch (RuntimeException failure) {
      new SecurityContextLogoutHandler().logout(request, response, verified);
      throw failure;
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    var authenticated = SecurityContextHolder.getContext().getAuthentication();
    var logout = new SecurityContextLogoutHandler();
    logout.setSecurityContextRepository(contexts);
    logout.logout(request, response, authenticated);
    new CsrfLogoutHandler(csrf).logout(request, response, authenticated);
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from("JSESSIONID", "")
            .path("/")
            .httpOnly(true)
            .secure(secureCookies)
            .sameSite("Lax")
            .maxAge(0)
            .build()
            .toString());
    return ResponseEntity.noContent().build();
  }
}
