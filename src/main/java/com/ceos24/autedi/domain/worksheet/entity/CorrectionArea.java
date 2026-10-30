package com.ceos24.autedi.domain.worksheet.entity;

public enum CorrectionArea {
    GRAMMAR_SPELLING,
    GRAMMAR_MECHANICS,
    VOCABULARY_EXPRESSION,
    SENTENCE_STRUCTURE,
    CONTENT_SUMMARY;

    public static CorrectionArea fromString(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase();
        if ("GRAMMAR_MECHANICS".equals(normalized)) {
            return GRAMMAR_SPELLING;
        }
        return CorrectionArea.valueOf(normalized);
    }
}
