package com.ceos24.autedi.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ErrorCodeContractTest {
    @Test
    void publicErrorCodesHaveUniqueIdentifiers() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode).toList())
                .doesNotHaveDuplicates();
    }
}
