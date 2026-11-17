package com.instabazaar.enrichment.service;

import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ControlledVocabularyTagProvider implements TagSuggestionProvider {
  public static final String VOCABULARY_VERSION = "fashion-v1";
  private static final List<String> VOCABULARY =
      List.of(
          "dress",
          "kurti",
          "ethnic wear",
          "casual wear",
          "top",
          "cotton",
          "silk",
          "linen",
          "denim",
          "embroidered",
          "floral",
          "printed",
          "solid",
          "party wear",
          "festive",
          "wedding",
          "office wear",
          "red",
          "blue",
          "black",
          "white",
          "green");

  private String tokens(String value) {
    return " "
        + Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .strip()
        + " ";
  }

  @Override
  public List<String> suggest(Content content) {
    Objects.requireNonNull(content);
    var fields =
        List.of(
            tokens(content.title()),
            tokens(content.category()),
            tokens(content.colour()),
            tokens(content.material()),
            tokens(content.occasion()),
            tokens(content.brand()),
            tokens(content.description()));
    int[] weights = {4, 3, 3, 3, 3, 3, 1};
    var scores = new HashMap<String, Integer>();
    for (String tag : VOCABULARY) {
      String phrase = tokens(tag);
      int score = 0;
      for (int i = 0; i < fields.size(); i++)
        if (fields.get(i).contains(phrase)) score += weights[i];
      if (score > 0) scores.put(tag, score);
    }
    return scores.keySet().stream()
        .sorted(
            Comparator.<String>comparingInt(scores::get)
                .reversed()
                .thenComparing(Comparator.naturalOrder()))
        .limit(20)
        .toList();
  }
}
