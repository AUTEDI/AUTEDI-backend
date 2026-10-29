package com.ceos24.autedi.global.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GeneralSuccessCode {
    OK(HttpStatus.OK, "COMMON_200", "성공입니다."),
    CREATED(HttpStatus.CREATED, "COMMON_201", "생성되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
