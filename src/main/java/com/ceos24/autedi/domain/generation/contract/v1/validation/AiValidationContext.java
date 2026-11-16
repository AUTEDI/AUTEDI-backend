package com.ceos24.autedi.domain.generation.contract.v1.validation;

import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 한 페이지의 불변 전사문·선택 설정 스냅샷. 이미지와 같은 페이지의 응답에만 사용한다. */
public record AiValidationContext(
        int pageNumber, List<String> transcriptionLines, Map<AiArea, AiCorrectionLevel> selectedAreas) {
    public AiValidationContext {
        if (pageNumber < 1) throw new IllegalArgumentException("페이지 번호는 1 이상이어야 합니다.");
        transcriptionLines = List.copyOf(transcriptionLines);
        selectedAreas = Map.copyOf(selectedAreas);
        if (transcriptionLines.stream().allMatch(String::isBlank)) {
            throw new IllegalArgumentException("읽을 글이 없는 전사문은 첨삭 없는 성공으로 처리할 수 없습니다.");
        }
        if (transcriptionLines.stream().anyMatch(line -> line.contains("\n") || line.contains("\r"))) {
            throw new IllegalArgumentException("전사문은 줄 단위로 전달해야 합니다.");
        }
    }

    /** CRLF/CR만 LF로 통일한다. 원문 공백·빈 줄·마지막 빈 줄을 삭제하지 않는다. */
    public static AiValidationContext fromText(
            int pageNumber, String transcription, Map<AiArea, AiCorrectionLevel> selectedAreas) {
        Objects.requireNonNull(transcription, "transcription");
        String normalized = transcription.replace("\r\n", "\n").replace('\r', '\n');
        return new AiValidationContext(pageNumber, Arrays.asList(normalized.split("\n", -1)), selectedAreas);
    }
}
