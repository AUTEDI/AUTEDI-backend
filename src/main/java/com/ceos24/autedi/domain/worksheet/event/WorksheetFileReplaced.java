package com.ceos24.autedi.domain.worksheet.event;

/** QR 로그인 클라이언트(휴대폰)의 업로드로 기존 학습지 파일이 교체됐을 때 발행된다. userId는 수신자(작업 소유자). */
public record WorksheetFileReplaced(Long userId, String sessionId, Long worksheetId) {}
