package com.ceos24.autedi.domain.teacher.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 월별 집계에 쓰는 시각 범위. {@code from} 이상 {@code to} 미만이다.
 *
 * <p>저장 시각은 한국 시간의 {@code LocalDateTime}이므로 월 경계도 한국 시간 기준이다. "이번 달"은 서버 설정과 무관하게 {@link #ZONE}으로
 * 구한다.
 */
public record MonthRange(LocalDateTime from, LocalDateTime to) {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 전체 기간의 시작과 끝. 실제 저장 시각은 이 범위 안에 있으며, DB의 날짜 범위(1000~9999년) 안의 값이다. */
    private static final LocalDateTime EARLIEST = LocalDateTime.of(2000, 1, 1, 0, 0);

    private static final LocalDateTime LATEST = LocalDateTime.of(9999, 1, 1, 0, 0);

    /** 해당 월의 1일 00:00 이상, 다음 달 1일 00:00 미만. */
    public static MonthRange of(YearMonth month) {
        return new MonthRange(
                month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    /** 한국 시간 기준 이번 달. */
    public static MonthRange current() {
        return of(YearMonth.now(ZONE));
    }

    /** 기간 제한 없음. */
    public static MonthRange all() {
        return new MonthRange(EARLIEST, LATEST);
    }
}
