package com.instabazaar.catalog.repository;

import com.instabazaar.shared.repository.SqlAccess;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Serialize catalogue writes for the single-instance MVP, including cross-table search refreshes.
 */
@Repository
public class CatalogMutationLock {
  private final SqlAccess sql;

  public CatalogMutationLock(SqlAccess sql) {
    this.sql = sql;
  }

  public void acquire() {
    sql.rows("select pg_advisory_xact_lock(734822020)", Map.of());
  }
}
