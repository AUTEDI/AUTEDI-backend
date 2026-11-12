package com.ceos24.autedi.global.sse;

import com.ceos24.autedi.global.security.CustomUserPrincipal;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "00. Events (SSE)", description = "실시간 이벤트 스트림. 응답은 ApiResponse 봉투가 아니라 text/event-stream 형식입니다.")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SseEmitterRegistry registry;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
            summary = "SSE 구독 (인증)",
            description = "Authorization: Bearer 헤더로 연결합니다. 액세스 토큰 만료 시각이 지나면 서버가 연결을 닫습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description =
                        "text/event-stream. 형식: id/event/data({eventId, occurredAt, ...payload}). 이벤트: qr.scanned, worksheet.created, worksheet.file_replaced, generation.started, generation.progress, generation.succeeded, generation.failed. 25초마다 ': ping' 주석, 토큰 만료 시 서버가 연결을 닫음.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "text/event-stream",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                value =
                                                        "event: generation.failed\ndata: {\"eventId\":\"e1\",\"occurredAt\":\"2026-11-11T14:31:02\",\"worksheetId\":12,\"generationId\":34,\"errorCode\":\"OCR_FAILED\",\"retryable\":true}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "AUTH_001: 인증이 필요합니다.",
                content =
                        @io.swagger.v3.oas.annotations.media.Content(
                                mediaType = "application/json",
                                examples =
                                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                name = "AUTH_001",
                                                value =
                                                        "{\"isSuccess\":false,\"code\":\"AUTH_001\",\"message\":\"인증이 필요합니다.\",\"result\":null,\"error\":null}")))
    })
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserPrincipal principal,
            HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        // 인증 필터를 통과했으므로 Bearer 토큰이 유효하다. 토큰 값은 로그에 남기지 않는다.
        Instant expiresAt = jwtTokenProvider.getExpiration(header.substring(BEARER_PREFIX.length()));

        SseEmitterRegistry.Connection connection = registry.register(principal.userId(), expiresAt);
        try {
            // 초기 주석 한 줄로 헤더를 flush해 프록시 버퍼링을 막는다.
            connection.emitter().send(SseEmitter.event().comment(" connected"));
        } catch (IOException | RuntimeException e) {
            registry.remove(principal.userId(), connection);
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(connection.emitter());
    }
}
