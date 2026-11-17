package com.instabazaar.security;

import com.instabazaar.account.service.AccountService;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.web.HttpErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rechecks account state on every session request so suspension takes effect immediately. */
public final class SessionAccountFilter extends OncePerRequestFilter {
  private final AccountService accounts;
  private final HttpErrors errors;

  public SessionAccountFilter(AccountService accounts, HttpErrors errors) {
    this.accounts = accounts;
    this.errors = errors;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof SessionPrincipal principal
        && !request.getRequestURI().equals(request.getContextPath() + "/api/v1/auth/logout")) {
      try {
        var user = accounts.me();
        if (!user.enabled()) {
          throw new BusinessException("ACCOUNT_DISABLED", 403, "Account is disabled.");
        }
        var fresh =
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(fresh);
        SecurityContextHolder.setContext(context);
      } catch (RuntimeException failure) {
        SecurityContextHolder.clearContext();
        if (failure instanceof BusinessException business
            && (business.getStatus() == 401
                || business.getStatus() == 403
                || business.getStatus() == 404)) {
          var session = request.getSession(false);
          if (session != null) session.invalidate();
        }
        errors.write(failure, request, response);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
