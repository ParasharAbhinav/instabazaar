package com.instabazaar.catalog.service;

import com.instabazaar.account.model.Role;
import com.instabazaar.catalog.model.Category;
import com.instabazaar.catalog.repository.*;
import com.instabazaar.search.service.SearchDocumentService;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.validation.Rules;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {
  private final CategoryRepository categories;
  private final CurrentUser current;
  private final CatalogMutationLock mutation;
  private final SearchDocumentService search;

  public CategoryService(
      CategoryRepository categories,
      CurrentUser current,
      CatalogMutationLock mutation,
      SearchDocumentService search) {
    this.categories = categories;
    this.current = current;
    this.mutation = mutation;
    this.search = search;
  }

  @Transactional(readOnly = true)
  public Contracts.Items<Contracts.Category> active() {
    return new Contracts.Items<>(
        categories.active().stream()
            .map(
                c ->
                    new Contracts.Category(c.getId(), c.getName(), c.getSlug(), c.getDescription()))
            .toList());
  }

  public Category requireActive(UUID id) {
    var c = categories.get(Rules.required(id, "categoryId"));
    if (!c.isActive()) throw BusinessException.invalid("categoryId", "Category is inactive.");
    return c;
  }

  /** Internal administrator maintenance operation; no category mutation API is introduced. */
  public void rename(UUID id, String name) {
    current.require(Role.ADMIN);
    mutation.acquire();
    var c = categories.lock(id);
    c.setName(Rules.name(name, "name", 80));
    search.category(id);
  }
}
