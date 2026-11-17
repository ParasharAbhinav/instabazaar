package com.instabazaar.enrichment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReelTranscriptEnrichmentServiceTest {

    private ReelTranscriptEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new ReelTranscriptEnrichmentService();
    }

    @Test
    void extractsDomainKeywordsFromSpokenReelTranscript() {
        var result = service.analyzeReelTranscript(
                "Indigo Archive Piece",
                "Hey everyone, showing this vintage handloom cotton kurta with block print detailing and oversized fit."
        );

        assertFalse(result.extractedKeywords().isEmpty());
        assertTrue(result.extractedKeywords().contains("handloom"));
        assertTrue(result.extractedKeywords().contains("cotton"));
        assertTrue(result.extractedKeywords().contains("kurta"));
        assertTrue(result.extractedKeywords().contains("vintage"));
        assertTrue(result.searchIndexSummary().contains("handloom"));
    }

    @Test
    void rejectsBlankReelTranscript() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyzeReelTranscript("Kurta", "   ")
        );
    }
}