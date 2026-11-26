package com.ceos24.autedi.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Password123!", "abcdef1!", "abcdefghij12345678!@", "한글abc12!@"})
    void 정책을_만족하면_통과한다(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "abc12!x", // 7자
                "abcdefghij123456789!@", // 21자
                "Password!!!!", // 숫자 없음
                "12345678!@#$", // 영문 없음
                "Password1234", // 특수문자 없음
                "Pass word1!", // 공백 포함
                "        ", // 공백만
                "",
            })
    void 정책을_어기면_실패한다(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void null은_통과시킨다() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
