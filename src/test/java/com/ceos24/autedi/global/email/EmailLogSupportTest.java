package com.ceos24.autedi.global.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class EmailLogSupportTest {

    @ParameterizedTest
    @CsvSource({
        "teacher@naver.com, naver.com",
        "Teacher@Naver.COM, naver.com",
        "a@b@gmail.com, gmail.com",
        "teacher@ naver.com , naver.com"
    })
    @DisplayName("이메일에서 소문자 도메인만 꺼낸다")
    void domainOf(String email, String expected) {
        assertThat(EmailLogSupport.domainOf(email)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("주소가 없으면 unknown")
    void domainOfBlank(String email) {
        assertThat(EmailLogSupport.domainOf(email)).isEqualTo("unknown");
    }

    @ParameterizedTest
    @CsvSource({"no-at-sign", "teacher@"})
    @DisplayName("@가 없거나 도메인이 비어 있으면 unknown")
    void domainOfMalformed(String email) {
        assertThat(EmailLogSupport.domainOf(email)).isEqualTo("unknown");
    }

    @Test
    @DisplayName("causeType은 가장 깊은 원인 예외의 클래스 이름이다")
    void causeTypeIsRootCause() {
        Exception error = new IllegalStateException("outer", new RuntimeException("mid", new IOException("root")));

        assertThat(EmailLogSupport.causeType(error)).isEqualTo("IOException");
    }

    @Test
    @DisplayName("원인이 없으면 자기 자신의 이름이다")
    void causeTypeWithoutCause() {
        assertThat(EmailLogSupport.causeType(new IllegalStateException("only"))).isEqualTo("IllegalStateException");
    }
}
