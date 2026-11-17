package com.instabazaar.catalog.service;

import com.instabazaar.catalog.repository.CatalogReadRepository;
import com.instabazaar.shared.dto.Contracts.*;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.validation.Rules;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogReadService {
  private final CatalogReadRepository reads;
  private final com.instabazaar.security.CurrentUser current;

  public CatalogReadService(
      CatalogReadRepository reads, com.instabazaar.security.CurrentUser current) {
    this.reads = reads;
    this.current = current;
  }

  public void requirePublic(UUID id) {
    Rules.required(id, "productId");
    if (!reads.isPublic(id)) throw BusinessException.missing();
  }

  public ProductDetail publicProduct(UUID id) {
    Rules.required(id, "productId");
    var result = reads.products(List.of(id), true).get(id);
    if (result == null) throw BusinessException.missing();
    return result.product();
  }

  /**
   * Internal projection used only after the calling service has authorized ownership/admin access.
   */
  public ManagedProduct managed(UUID id) {
    Rules.required(id, "productId");
    var actor = current.require();
    if (actor.getRole() != com.instabazaar.account.model.Role.ADMIN
        && !reads.ownsProduct(id, actor.getId())) throw BusinessException.missing();
    var result = reads.products(List.of(id), false).get(id);
    if (result == null) throw BusinessException.missing();
    return result;
  }

  public Store store(UUID id, boolean publicOnly) {
    Rules.required(id, "sellerId");
    if (!publicOnly) {
      var actor = current.require();
      if (actor.getRole() != com.instabazaar.account.model.Role.ADMIN
          && !reads.ownsStore(id, actor.getId())) throw BusinessException.missing();
    }
    var result = reads.stores(List.of(id), publicOnly).get(id);
    if (result == null) throw BusinessException.missing();
    return result;
  }

  public Map<UUID, ProductSummary> publicSummaries(List<UUID> ids) {
    var result = new HashMap<UUID, ProductSummary>();
    reads.products(ids, true).forEach((id, p) -> result.put(id, CatalogReadRepository.summary(p)));
    return result;
  }
}
