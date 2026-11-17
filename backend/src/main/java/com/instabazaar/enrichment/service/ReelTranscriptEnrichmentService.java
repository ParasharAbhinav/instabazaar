package com.instabazaar.enrichment.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts normalized, searchable fashion/thrift keywords from a seller's
 * Instagram Reel audio transcript or spoken voiceover caption for PostgreSQL TSVECTOR indexing.
 */
@Service
public class ReelTranscriptEnrichmentService {

    private static final int MAX_TRANSCRIPT_LENGTH = 2_000;
    private static final int MAX_EXTRACTED_KEYWORDS = 12;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]{3,24}");

    private static final Set<String> DOMAIN_VOCABULARY = Set.of(
            "handloom", "kurta", "saree", "chanderi", "banarasi", "linen", "cotton",
            "silk", "khadi", "denim", "selvedge", "vintage", "thrift", "archival",
            "oversized", "corduroy", "indigo", "terracotta", "embroidered", "blockprint",
            "chikankari", "kalamkari", "jacket", "trousers", "shirt", "handwoven",
            "unisex", "deadstock", "streetwear", "workwear", "flannel", "wool"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "this", "that", "from", "have", "has",
            "are", "was", "were", "will", "your", "our", "very", "just", "like",
            "new", "reel", "video", "dm", "whatsapp", "price", "shipping", "available",
            "everyone", "hey", "showing", "piece"
    );

    public record ReelEnrichmentResult(
            String normalizedTranscript,
            List<String> extractedKeywords,
            String searchIndexSummary
    ) {}

    public ReelEnrichmentResult analyzeReelTranscript(String rawTitle, String rawTranscript) {
        String safeTitle = rawTitle == null ? "" : rawTitle.trim();
        String safeTranscript = rawTranscript == null ? "" : rawTranscript.trim();

        if (safeTranscript.isEmpty()) {
            throw new IllegalArgumentException("Reel transcript or spoken caption must not be empty.");
        }
        if (safeTranscript.length() > MAX_TRANSCRIPT_LENGTH) {
            throw new IllegalArgumentException("Reel transcript exceeds maximum length of 2000 characters.");
        }

        String normalized = safeTranscript.replaceAll("\\s+", " ").trim();
        LinkedHashSet<String> keywords = new LinkedHashSet<>();

        String lower = (safeTitle + " " + normalized).toLowerCase(Locale.ROOT)
                .replace("block print", "blockprint")
                .replace("hand woven", "handwoven");

        Matcher matcher = TOKEN_PATTERN.matcher(lower);
        List<String> fallbackTokens = new ArrayList<>();
        while (matcher.find() && keywords.size() < MAX_EXTRACTED_KEYWORDS) {
            String token = matcher.group();
            if (DOMAIN_VOCABULARY.contains(token)) {
                keywords.add(token);
            } else if (!STOP_WORDS.contains(token) && token.length() >= 4) {
                fallbackTokens.add(token);
            }
        }

        for (String token : fallbackTokens) {
            if (keywords.size() >= MAX_EXTRACTED_KEYWORDS) {
                break;
            }
            keywords.add(token);
        }

        List<String> finalKeywords = List.copyOf(keywords);
        String searchSummary = String.join(" ", finalKeywords);

        return new ReelEnrichmentResult(normalized, finalKeywords, searchSummary);
    }
}