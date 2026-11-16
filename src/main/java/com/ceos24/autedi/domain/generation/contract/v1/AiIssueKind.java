package com.ceos24.autedi.domain.generation.contract.v1;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AiIssueKind {
    @JsonProperty("grammar")
    GRAMMAR,
    @JsonProperty("spelling")
    SPELLING,
    @JsonProperty("punctuation")
    PUNCTUATION,
    @JsonProperty("vocabulary")
    VOCABULARY,
    @JsonProperty("sentence_structure")
    SENTENCE_STRUCTURE,
    @JsonProperty("content")
    CONTENT,
    @JsonProperty("ocr_mismatch")
    OCR_MISMATCH,
    @JsonProperty("illegible")
    ILLEGIBLE,
    @JsonProperty("unclear_intent")
    UNCLEAR_INTENT,
    @JsonProperty("image_problem")
    IMAGE_PROBLEM
}
