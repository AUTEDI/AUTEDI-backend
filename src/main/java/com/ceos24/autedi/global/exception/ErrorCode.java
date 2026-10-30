package com.ceos24.autedi.global.exception;

import com.ceos24.autedi.global.response.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements BaseErrorCode {

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
    INVITATION_EMAIL_MISMATCH(HttpStatus.BAD_REQUEST, "INVITATION_004", "초대받은 이메일과 가입 요청 이메일이 일치하지 않습니다."),

    // Worksheet & File
    PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED, "COMMON_412", "데이터 버전이 변경되었습니다. 최신 데이터를 조회한 후 다시 시도해 주세요."),
    WORKSHEET_NOT_FOUND(HttpStatus.NOT_FOUND, "WORKSHEET_001", "학습지를 찾을 수 없거나 접근 권한이 없습니다."),
    INVALID_WORKSHEET_STATE(HttpStatus.CONFLICT, "WORKSHEET_002", "현재 학습지 상태에서는 요청한 작업을 수행할 수 없습니다."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "FILE_001", "업로드할 파일이 비어 있습니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_002", "지원하지 않는 파일 형식입니다. JPG, PNG, PDF 파일만 허용됩니다."),
    FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_003", "파일 저장 중 오류가 발생했습니다."),

    // Generation
    GENERATION_IN_PROGRESS(HttpStatus.CONFLICT, "GENERATION_001", "이미 생성 작업이 진행 중입니다."),
    ALREADY_GENERATED(HttpStatus.CONFLICT, "GENERATION_002", "이미 생성이 완료된 학습지입니다."),
    GENERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "GENERATION_003", "생성 작업을 찾을 수 없습니다."),
    NO_SETTINGS_SELECTED(HttpStatus.BAD_REQUEST, "GENERATION_004", "첨삭 영역을 최소 한 개 선택해 주세요."),
    CANNOT_RETRY_GENERATION(HttpStatus.CONFLICT, "GENERATION_005", "재시도할 수 없는 작업 상태이거나 파일/설정이 변경되었습니다."),

    // Correction
    CORRECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CORRECTION_001", "첨삭 항목을 찾을 수 없습니다."),
    CANNOT_MODIFY_DELETED_CORRECTION(HttpStatus.CONFLICT, "CORRECTION_002", "삭제된 첨삭 항목은 수정할 수 없습니다."),

    // Comment
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_001", "코멘트를 찾을 수 없거나 이미 삭제되었습니다."),
    COMMENT_PAGE_MISMATCH(HttpStatus.BAD_REQUEST, "COMMENT_002", "코멘트 영역의 페이지가 대상 페이지와 일치하지 않습니다."),
    PAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "PAGE_001", "해당 페이지를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
