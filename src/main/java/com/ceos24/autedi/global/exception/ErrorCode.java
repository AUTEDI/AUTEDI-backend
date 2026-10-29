package com.ceos24.autedi.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_002", "허용되지 않은 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "서버 내부 오류가 발생했습니다."),

    // Auth & Security
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_002", "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "만료된 토큰입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_005", "이메일 또는 비밀번호가 올바르지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_006", "존재하지 않는 회원입니다."),
    USER_INACTIVE(HttpStatus.FORBIDDEN, "AUTH_007", "비활성화된 계정입니다."),
    USER_LOCKED(HttpStatus.FORBIDDEN, "AUTH_008", "잠금 처리된 계정입니다."),
    ALREADY_REGISTERED_EMAIL(HttpStatus.CONFLICT, "AUTH_009", "이미 가입된 이메일입니다."),

    // Academy
    ACADEMY_NOT_FOUND(HttpStatus.NOT_FOUND, "ACADEMY_001", "존재하지 않는 학원입니다."),
    ACADEMY_SUBSCRIPTION_INACTIVE(HttpStatus.FORBIDDEN, "ACADEMY_002", "학원 유료 구독이 활성 상태가 아닙니다."),

    // Invitation
    INVALID_INVITATION_TOKEN(HttpStatus.NOT_FOUND, "INVITATION_001", "유효하지 않은 초대 토큰입니다."),
    EXPIRED_INVITATION_TOKEN(HttpStatus.GONE, "INVITATION_002", "만료된 초대 링크입니다."),
    ALREADY_USED_INVITATION(HttpStatus.BAD_REQUEST, "INVITATION_003", "이미 사용되었거나 취소된 초대 링크입니다."),
    INVITATION_EMAIL_MISMATCH(HttpStatus.BAD_REQUEST, "INVITATION_004", "초대받은 이메일과 가입 요청 이메일이 일치하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
