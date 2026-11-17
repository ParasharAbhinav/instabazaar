package com.instabazaar.account.repository;

import com.instabazaar.account.model.User;
import com.instabazaar.shared.repository.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  private final EntityAccess jpa;
  private final SqlAccess sql;

  public UserRepository(EntityAccess jpa, SqlAccess sql) {
    this.jpa = jpa;
    this.sql = sql;
  }

  public User get(UUID id) {
    return jpa.find(User.class, id);
  }

  public User lock(UUID id) {
    return jpa.lock(User.class, id);
  }

  public User fresh(UUID id) {
    return jpa.query("select u from User u where u.id=:id", User.class, Map.of("id", id)).stream()
        .findFirst()
        .orElseThrow(com.instabazaar.shared.exception.BusinessException::missing);
  }

  public boolean enabled(UUID id) {
    return sql.count("select count(*) from app_user where id=:id and enabled", Map.of("id", id))
        == 1;
  }

  public String normalizeEmail(String email) {
    return sql.string("select lower(btrim(:email))", Map.of("email", email));
  }

  public Optional<User> byEmail(String email) {
    return jpa
        .query("select u from User u where u.email=:email", User.class, Map.of("email", email))
        .stream()
        .findFirst();
  }

  public long enabledAdmins() {
    return sql.count("select count(*) from app_user where enabled and role='ADMIN'", Map.of());
  }

  public void serializeAccountChanges() {
    sql.adminLock();
  }
}
