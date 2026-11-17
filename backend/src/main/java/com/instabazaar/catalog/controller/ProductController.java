package com.instabazaar.catalog.controller;

import com.instabazaar.catalog.service.CatalogReadService;
import com.instabazaar.search.dto.SearchCriteria;
import com.instabazaar.search.service.SearchService;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
  private static final Set<String> FILTERS =
      Set.of(
          "q",
          "categoryId",
          "sellerId",
          "minPrice",
          "maxPrice",
          "colour",
          "sizeCode",
          "available",
          "sort",
          "page",
          "size");
  private final SearchService search;
  private final CatalogReadService catalog;

  public ProductController(SearchService search, CatalogReadService catalog) {
    this.search = search;
    this.catalog = catalog;
  }

  @GetMapping
  public Contracts.Page<Contracts.ProductSummary> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID sellerId,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(required = false) String colour,
      @RequestParam(required = false) String sizeCode,
      @RequestParam(required = false) Boolean available,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    for (var entry : request.getParameterMap().entrySet()) {
      if (!FILTERS.contains(entry.getKey()) || entry.getValue().length != 1) {
        throw new BusinessException("INVALID_FILTER", 400, "Unknown or repeated search parameter.");
      }
    }
    return search.search(
        new SearchCriteria(
            q,
            categoryId,
            sellerId,
            minPrice,
            maxPrice,
            colour,
            sizeCode,
            available,
            sort,
            page,
            size));
  }

  @GetMapping("/{id}")
  public Contracts.ProductDetail detail(@PathVariable UUID id) {
    return catalog.publicProduct(id);
  }
}
