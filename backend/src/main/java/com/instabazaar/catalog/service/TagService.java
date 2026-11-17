package com.instabazaar.catalog.service;

import com.instabazaar.account.model.Role;
import com.instabazaar.catalog.model.*;
import com.instabazaar.catalog.repository.*;
import com.instabazaar.enrichment.service.TagSuggestionProvider;
import com.instabazaar.search.service.SearchDocumentService;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts.*;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.repository.EntityAccess;
import com.instabazaar.shared.validation.Rules;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TagService {
  private final CurrentUser current;
  private final ProductRepository products;
  private final EntityAccess jpa;
  private final TagSuggestionProvider provider;
  private final SearchDocumentService search;
  private final CatalogMutationLock mutation;

  public TagService(
      CurrentUser current,
      ProductRepository products,
      EntityAccess jpa,
      TagSuggestionProvider provider,
      SearchDocumentService search,
      CatalogMutationLock mutation) {
    this.current = current;
    this.products = products;
    this.jpa = jpa;
    this.provider = provider;
    this.search = search;
    this.mutation = mutation;
  }

  @Transactional(readOnly = true)
  public TagSet list(UUID id) {
    var p = products.owned(id, current.require(Role.SELLER).getId(), false);
    return map(p);
  }

  public TagSet suggest(UUID id, Long version) {
    var actor = current.require(Role.SELLER);
    mutation.acquire();
    var p = products.owned(id, actor.getId(), true);
    check(p, version);
    var existing = new HashSet<String>();
    products.tags(id).forEach(t -> existing.add(t.getNormalizedTag()));
    var content =
        new TagSuggestionProvider.Content(
            p.getTitle(),
            p.getCategory().getName(),
            p.getColour(),
            p.getMaterial(),
            p.getOccasion(),
            p.getBrand(),
            p.getDescription() + " " + Objects.toString(p.getReelCaption(), ""));
    var suggestions = provider.suggest(content);
    Rules.collection(suggestions, "suggestions", 20);
    for (String tag : suggestions) {
      tag = Rules.name(tag, "suggestions", 60);
      if (existing.add(products.lower(tag)))
        jpa.persist(new ProductTag(p, tag, TagSource.SYSTEM, TagStatus.SUGGESTED));
    }
    products.changed(p, version);
    search.product(id);
    return map(p);
  }

  public TagSet decide(UUID id, UUID tagId, TagStatus status, Long version) {
    if (status != TagStatus.APPROVED && status != TagStatus.REJECTED)
      throw BusinessException.invalid("status", "Choose APPROVED or REJECTED.");
    var actor = current.require(Role.SELLER);
    mutation.acquire();
    var p = products.owned(id, actor.getId(), true);
    check(p, version);
    var tag =
        products.tags(id).stream()
            .filter(t -> t.getId().equals(tagId))
            .findFirst()
            .orElseThrow(BusinessException::missing);
    if (tag.getSource() != TagSource.SYSTEM) throw BusinessException.transition();
    tag.setStatus(status);
    products.changed(p, version);
    search.product(id);
    return map(p);
  }

  private void check(Product p, Long version) {
    Rules.version(version, p.getVersion());
    if (p.getListingStatus() == ListingStatus.ARCHIVED) throw BusinessException.transition();
  }

  private TagSet map(Product p) {
    return new TagSet(
        products.tags(p.getId()).stream()
            .map(t -> new Tag(t.getId(), t.getTag(), t.getSource().name(), t.getStatus().name()))
            .toList(),
        p.getVersion());
  }
}
