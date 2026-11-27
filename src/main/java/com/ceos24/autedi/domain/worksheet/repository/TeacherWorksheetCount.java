package com.ceos24.autedi.domain.worksheet.repository;

/** 선생님별 학습지 건수. 월별 첨삭 수 집계 결과 한 행이다. */
public record TeacherWorksheetCount(Long teacherId, long count) {}
