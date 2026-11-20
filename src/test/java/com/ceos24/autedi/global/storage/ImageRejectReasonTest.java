package com.ceos24.autedi.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ImageRejectReasonTest {

    @ParameterizedTest
    @EnumSource(ImageRejectReason.class)
    @DisplayName("모든 사유는 사용자 문구를 가진다")
    void everyReasonHasMessage(ImageRejectReason reason) {
        assertThat(reason.getMessage()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(ImageRejectReason.class)
    @DisplayName("사유 이름은 공백 없는 대문자 토큰이다 (로그의 reason=값 규칙)")
    void nameIsLogToken(ImageRejectReason reason) {
        assertThat(reason.name()).matches("[A-Z][A-Z0-9_]*");
    }

    @Test
    @DisplayName("예외 메시지는 사유 문구와 같고 IllegalArgumentException으로 잡힌다")
    void exceptionKeepsMessageAndType() {
        ImageRejectedException exception = new ImageRejectedException(ImageRejectReason.EXIF_HEADER_CORRUPT);

        assertThat(exception).isInstanceOf(IllegalArgumentException.class);
        assertThat(exception.getMessage()).isEqualTo("손상된 EXIF 헤더입니다.");
        assertThat(exception.getReason()).isEqualTo(ImageRejectReason.EXIF_HEADER_CORRUPT);
    }
}
