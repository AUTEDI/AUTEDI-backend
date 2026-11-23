package com.ceos24.autedi.global.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiErrorLogger {

    public static void log(ErrorCode errorCode) {
        log(errorCode, null);
    }

    public static void log(ErrorCode errorCode, Throwable cause) {
        Level level = errorCode.getLogLevel();
        // 스택트레이스는 ERROR(5xx)일 때만 붙인다
        Throwable stack = (level == Level.ERROR) ? cause : null;

        log.atLevel(level)
                .setCause(stack)
                .log(
                        "[api] 요청 실패: errorCode={}, status={}",
                        errorCode.getCode(),
                        errorCode.getStatus().value());
    }
}
