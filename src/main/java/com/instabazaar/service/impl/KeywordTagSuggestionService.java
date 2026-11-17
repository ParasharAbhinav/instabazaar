package com.instabazaar.service.impl;

import com.instabazaar.service.TagSuggestionService;
import com.instabazaar.util.Text;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class KeywordTagSuggestionService implements TagSuggestionService {
    private static final List<String> VOCABULARY = List.of("dress", "kurti", "kurta", "ethnic wear", "casual wear",
            "top", "cotton", "silk", "linen", "denim", "embroidered", "floral", "printed", "solid", "party wear",
            "festive", "wedding", "office wear", "red", "blue", "black", "white", "green", "handloom", "saree",
            "chanderi", "khadi", "vintage", "thrift", "oversized", "corduroy", "indigo", "terracotta", "blockprint",
            "chikankari", "jacket", "trousers", "shirt", "handwoven", "unisex", "streetwear", "workwear");
    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("ivory", List.of("white")), Map.entry("olive", List.of("green")),
            Map.entry("indigo", List.of("blue")), Map.entry("rose", List.of("pink")),
            Map.entry("evening", List.of("party wear")), Map.entry("party", List.of("party wear")),
            Map.entry("workwear", List.of("office wear")), Map.entry("kurta", List.of("kurti")),
            Map.entry("embroidery", List.of("embroidered")), Map.entry("block print", List.of("blockprint")));

    @Override public List<String> suggest(String content) {
        String normalized = " " + Text.normalized(content) + " ";
        Set<String> result = new LinkedHashSet<>();
        VOCABULARY.stream().filter(word -> normalized.contains(" " + word + " ")).forEach(result::add);
        ALIASES.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .filter(entry -> normalized.contains(" " + entry.getKey() + " "))
                .forEach(entry -> result.addAll(entry.getValue()));
        return result.stream().limit(24).toList();
    }
}
