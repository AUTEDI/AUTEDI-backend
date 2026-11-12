package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 같은 페이지·전사 버전에 속하는 확정 원문. 설정·레벨 정책은 이 위치 검사에 포함하지 않는다. */
public record AiLocationContext(int pageNumber, List<String> transcriptionLines) {
    public AiLocationContext {
        if (pageNumber < 1) throw new IllegalArgumentException("페이지 번호는 1 이상이어야 합니다.");
        transcriptionLines = List.copyOf(transcriptionLines);
        if (transcriptionLines.stream().allMatch(String::isBlank)) {
            throw new IllegalArgumentException("읽을 글이 없는 전사는 첨삭 없는 정상 결과로 처리할 수 없습니다.");
        }
        if (transcriptionLines.stream().anyMatch(line -> line.contains("\n") || line.contains("\r"))) {
            throw new IllegalArgumentException("전사문은 줄 단위로 전달해야 합니다.");
        }
    }

    /** 줄바꿈만 LF로 통일한다. 원문의 공백·빈 줄·마지막 빈 줄은 보존한다. */
    public static AiLocationContext fromText(int pageNumber, String transcription) {
        Objects.requireNonNull(transcription, "transcription");
        String normalized = transcription.replace("\r\n", "\n").replace('\r', '\n');
        return new AiLocationContext(pageNumber, Arrays.asList(normalized.split("\n", -1)));
    }
}
