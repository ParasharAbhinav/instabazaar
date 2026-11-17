package com.instabazaar.moderation.repository;

import com.instabazaar.moderation.model.Report;
import com.instabazaar.shared.repository.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {
  private final EntityAccess jpa;
  private final SqlAccess sql;

  public ReportRepository(EntityAccess jpa, SqlAccess sql) {
    this.jpa = jpa;
    this.sql = sql;
  }

  public Report lock(UUID id) {
    return jpa.lock(Report.class, id);
  }

  public boolean openExists(UUID reporter, UUID target, boolean product) {
    return sql.count(
            "select count(*) from report where reporter_id=:reporter and status='OPEN' and "
                + (product ? "product_id" : "review_id")
                + "=:target",
            Map.of("reporter", reporter, "target", target))
        > 0;
  }
}
