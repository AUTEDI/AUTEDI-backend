package com.ceos24.autedi.domain.generation.contract.v1.validation;

/** 페이지별·대표 영역별 카드 수 기준. 권장 수는 최소 개수가 아니다. */
public enum AiCorrectionLevel {
    STANDARD(3, 5),
    ADVANCED(5, 7);

    private final int recommendedCount;
    private final int maximumCount;

    AiCorrectionLevel(int recommendedCount, int maximumCount) {
        this.recommendedCount = recommendedCount;
        this.maximumCount = maximumCount;
    }

    public int recommendedCount() {
        return recommendedCount;
    }

    public int maximumCount() {
        return maximumCount;
    }
}
