package com.ceos24.autedi.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 페이지로 나눈 목록 응답. 기존 목록 응답({@code items}, {@code total})에 페이지 정보를 더한 형태다. */
@Schema(description = "페이지 목록 응답")
public record PageResponse<T>(
        @Schema(description = "현재 페이지의 항목") List<T> items,
        @Schema(description = "조건에 맞는 전체 항목 수", example = "42") long total,
        @Schema(description = "현재 페이지 번호(0부터 시작)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "전체 페이지 수", example = "3") int totalPages) {

    /**
     * 이미 정렬이 끝난 전체 목록에서 요청한 페이지만 잘라 응답을 만든다. 범위를 벗어난 페이지는 빈 목록이다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @param size 1 이상의 페이지 크기
     */
    public static <T> PageResponse<T> fromAll(List<T> sortedAll, int page, int size) {
        long total = sortedAll.size();
        int from = (int) Math.min((long) page * size, total);
        int to = (int) Math.min(from + (long) size, total);
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(List.copyOf(sortedAll.subList(from, to)), total, page, size, totalPages);
    }
}
