package com.ceos24.autedi.domain.auth.event;

/** QR이 스캔되어 SCANNED로 전환됐을 때 발행되는 도메인 이벤트. qrToken은 담지 않는다. */
public record QrSessionScanned(Long userId, String sessionId, String deviceInfo) {}
