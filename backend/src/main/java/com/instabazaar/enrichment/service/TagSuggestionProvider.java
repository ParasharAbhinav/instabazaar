package com.instabazaar.enrichment.service;

import java.util.List;

public interface TagSuggestionProvider {
  record Content(
      String title,
      String category,
      String colour,
      String material,
      String occasion,
      String brand,
      String description) {}

  List<String> suggest(Content content);
}
