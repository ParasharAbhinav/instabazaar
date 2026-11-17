package com.instabazaar.shared.repository;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SqlAccess {
  private final NamedParameterJdbcTemplate jdbc;

  public SqlAccess(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> rows(String sql, Map<String, ?> args) {
    return jdbc.queryForList(sql, args);
  }

  public int update(String sql, Map<String, ?> args) {
    return jdbc.update(sql, args);
  }

  public long count(String sql, Map<String, ?> args) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, args, Long.class));
  }

  public String string(String sql, Map<String, ?> args) {
    return jdbc.queryForObject(sql, args, String.class);
  }

  public void adminLock() {
    jdbc.queryForList("select pg_advisory_xact_lock(734822019)", Map.of());
  }
}
