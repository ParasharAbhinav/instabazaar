package com.instabazaar.search.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SearchCriteria(
    String q,
    UUID categoryId,
    UUID sellerId,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    String colour,
    String sizeCode,
    Boolean available,
    String sort,
    int page,
    int size) {
  public static SearchCriteria defaults() {
    return new SearchCriteria(null, null, null, null, null, null, null, null, null, 0, 20);
  }
}
