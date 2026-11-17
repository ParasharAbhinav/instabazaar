package com.instabazaar.catalog.repository;

import com.instabazaar.catalog.model.Category;
import com.instabazaar.shared.repository.EntityAccess;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryRepository {
  private final EntityAccess jpa;

  public CategoryRepository(EntityAccess jpa) {
    this.jpa = jpa;
  }

  public Category get(UUID id) {
    return jpa.find(Category.class, id);
  }

  public Category lock(UUID id) {
    return jpa.lock(Category.class, id);
  }

  public List<Category> active() {
    return jpa.query(
        "select c from Category c where c.active=true order by c.name,c.id",
        Category.class,
        Map.of());
  }
}
