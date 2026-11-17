package com.instabazaar.account.service;

import com.instabazaar.account.model.*;
import com.instabazaar.account.repository.UserRepository;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.repository.EntityAccess;
import com.instabazaar.shared.validation.Rules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {
  private final CurrentUser current;
  private final UserRepository users;
  private final EntityAccess jpa;

  public AccountService(CurrentUser current, UserRepository users, EntityAccess jpa) {
    this.current = current;
    this.users = users;
    this.jpa = jpa;
  }

  @Transactional(readOnly = true)
  public Contracts.User me() {
    return map(current.require());
  }

  public Contracts.User updateName(String name, Long version) {
    var user = users.lock(current.require().getId());
    Rules.version(version, user.getVersion());
    user.setName(Rules.name(name, "name", 100));
    jpa.flush();
    return map(user);
  }

  public static Contracts.User map(User user) {
    return new Contracts.User(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getRole().name(),
        user.isEnabled(),
        user.getVersion(),
        user.getCreatedAt());
  }
}
