package com.instabazaar.security;

import com.instabazaar.account.model.*;
import com.instabazaar.account.repository.UserRepository;
import com.instabazaar.shared.exception.BusinessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public User require() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || !auth.isAuthenticated()
        || !(auth.getPrincipal() instanceof SessionPrincipal principal))
      throw new BusinessException("AUTHENTICATION_REQUIRED", 401, "Authentication is required.");
    if (!users.enabled(principal.userId()))
      throw new BusinessException("ACCOUNT_DISABLED", 403, "Account is disabled.");
    return users.fresh(principal.userId());
  }

  public User require(Role role) {
    User user = require();
    if (user.getRole() != role)
      throw new BusinessException("ACCESS_DENIED", 403, "This operation is not permitted.");
    return user;
  }
}
