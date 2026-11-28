package com.ceos24.autedi.domain.student.repository;

/** 결과 아카이브가 실제 참조한 전사. 재시도로 이전 작업의 전사를 재사용해도 연결을 유지한다. */
public record GrowthTranscriptionRow(
        Long generationId, Long worksheetId, Long pageId, Long transcriptionId, String sourceFileHash, String text) {}
