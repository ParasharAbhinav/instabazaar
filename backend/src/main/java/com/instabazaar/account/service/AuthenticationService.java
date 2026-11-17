package com.instabazaar.account.service;

import com.instabazaar.account.model.*;
import com.instabazaar.account.repository.UserRepository;
import com.instabazaar.security.SessionPrincipal;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.repository.EntityAccess;
import com.instabazaar.shared.validation.Rules;
import jakarta.validation.Validator;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
  private final UserRepository users;
  private final EntityAccess jpa;
  private final PasswordEncoder passwords;
  private final Validator validator;
  private final String dummyHash;

  public AuthenticationService(
      UserRepository users, EntityAccess jpa, PasswordEncoder passwords, Validator validator) {
    this.users = users;
    this.jpa = jpa;
    this.passwords = passwords;
    this.validator = validator;
    dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
  }

  @Transactional
  public Contracts.User register(String name, String email, String password, Role role) {
    if (role != Role.BUYER && role != Role.SELLER)
      throw BusinessException.invalid("role", "Choose BUYER or SELLER.");
    validatePassword(password);
    String normalized = email(email);
    if (users.byEmail(normalized).isPresent())
      throw new BusinessException("EMAIL_EXISTS", 409, "Email is already registered.");
    var user =
        new User(Rules.name(name, "name", 100), normalized, passwords.encode(password), role);
    if (!validator.validateProperty(user, "email").isEmpty())
      throw BusinessException.invalid("email", "Invalid email.");
    jpa.persist(user);
    jpa.flush();
    return AccountService.map(user);
  }

  /**
   * Verifies credentials only. HTTP adapter must rotate/persist session context and refresh CSRF.
   */
  @Transactional(readOnly = true)
  public Authentication verifyCredentials(String email, String password) {
    String normalized = email(email);
    Rules.text(password, "password", 128, false, false);
    var found = users.byEmail(normalized);
    boolean matched;
    try {
      matched = passwords.matches(password, found.map(User::getPasswordHash).orElse(dummyHash));
    } catch (IllegalArgumentException ex) {
      passwords.matches(password, dummyHash);
      matched = false;
    }
    if (found.isEmpty() || !matched)
      throw new BusinessException("INVALID_CREDENTIALS", 401, "Invalid credentials.");
    User user = found.get();
    if (!user.isEnabled())
      throw new BusinessException("ACCOUNT_DISABLED", 403, "Account is disabled.");
    return UsernamePasswordAuthenticationToken.authenticated(
        new SessionPrincipal(user.getId()),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
  }

  private String email(String value) {
    return users.normalizeEmail(Rules.name(value, "email", 254));
  }

  private void validatePassword(String value) {
    Rules.text(value, "password", 128, false, false);
    if (value.codePointCount(0, value.length()) < 12)
      throw BusinessException.invalid("password", "Use 12 to 128 characters.");
  }
}
