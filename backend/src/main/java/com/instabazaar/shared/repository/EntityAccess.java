package com.instabazaar.shared.repository;

import com.instabazaar.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Shared JPA mechanics; domain repositories own queries and lock choices. */
@Repository
public class EntityAccess {
  private final EntityManager em;

  public EntityAccess(EntityManager em) {
    this.em = em;
  }

  public <T> T find(Class<T> type, UUID id) {
    if (id == null) throw BusinessException.invalid("id", "Required.");
    T result = em.find(type, id);
    if (result == null) throw BusinessException.missing();
    return result;
  }

  public <T> T lock(Class<T> type, UUID id) {
    T value = find(type, id);
    em.refresh(value, LockModeType.PESSIMISTIC_WRITE);
    return value;
  }

  public <T> List<T> query(String jpql, Class<T> type, Map<String, ?> parameters) {
    var query = em.createQuery(jpql, type);
    parameters.forEach(query::setParameter);
    return query.getResultList();
  }

  public void persist(Object value) {
    em.persist(value);
  }

  public void remove(Object value) {
    em.remove(value);
  }

  public void flush() {
    try {
      em.flush();
    } catch (jakarta.persistence.OptimisticLockException ex) {
      throw BusinessException.conflict();
    } catch (jakarta.persistence.PersistenceException ex) {
      for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
        if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint) {
          String name = constraint.getConstraintName();
          if ("app_user_email_key".equals(name))
            throw new BusinessException("EMAIL_EXISTS", 409, "Email is already registered.");
          if ("uq_review_author_product".equals(name))
            throw new BusinessException("REVIEW_EXISTS", 409, "A review already exists.");
          if ("uq_open_product_report".equals(name) || "uq_open_review_report".equals(name))
            throw new BusinessException("REPORT_EXISTS", 409, "An open report already exists.");
          if ("product_image_asset_id_key".equals(name))
            throw new BusinessException(
                "ASSET_IN_USE", 409, "Asset is attached to another product.");
          if ("uq_product_tag".equals(name))
            throw BusinessException.invalid("sellerTags", "Duplicate normalized tag.");
          if ("uq_category_name_ci".equals(name))
            throw BusinessException.invalid("name", "Category name already exists.");
        }
      }
      throw ex;
    }
  }

  public void increment(Object value) {
    // Immediate Hibernate increment makes the returned version match the committed mutation,
    // including operations which only modify children.
    em.lock(value, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
  }
}
