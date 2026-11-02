package com.ceos24.autedi.domain.correction.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 불변 전사 PK를 JSON 내 논리 참조로 보존한다. start/end는 줄 내부 UTF-16이며 end 미포함이다. */
public record CorrectionAnchor(
        @JsonProperty("transcription_id") Long transcriptionId,
        @JsonProperty("line_id") int lineId,
        int occurrence,
        int start,
        int end) {
    public CorrectionAnchor {
        if (transcriptionId == null
                || transcriptionId < 1
                || lineId < 1
                || occurrence < 1
                || start < 0
                || end <= start) {
            throw new IllegalArgumentException("유효한 불변 전사·한 줄 anchor 연결이 필요합니다.");
        }
    }
}
