package com.ceos24.autedi.domain.generation.contract.v1_1;

/** 전사 줄 개수와 별개인 언어적 수정 범위. 허용 영역·동작 조합은 후속 검증 대상이다. */
public enum AiRevisionScope {
    WORD,
    PHRASE,
    CLAUSE,
    SENTENCE,
    SENTENCE_GROUP
}
