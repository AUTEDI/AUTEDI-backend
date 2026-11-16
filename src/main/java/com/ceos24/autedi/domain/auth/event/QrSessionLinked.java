package com.ceos24.autedi.domain.auth.event;

/** 이미 로그인된 같은 사용자의 폰이 승인 없이 QR 세션에 연결됐을 때 발행되는 도메인 이벤트. qrToken과 토큰류는 담지 않는다. */
public record QrSessionLinked(Long userId, String sessionId, String deviceInfo) {}
