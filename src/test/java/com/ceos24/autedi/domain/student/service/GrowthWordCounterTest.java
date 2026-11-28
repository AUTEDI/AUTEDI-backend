package com.ceos24.autedi.domain.student.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GrowthWordCounterTest {
    static Stream<Arguments> tokens() {
        return Stream.of(
                Arguments.of("Hello, world!", 2),
                Arguments.of("don't can’t well-known", 3),
                Arguments.of("one—two", 2),
                Arguments.of("one\ntwo\tthree", 3),
                Arguments.of("123 3rd grade", 3),
                Arguments.of("!!! ...", 0),
                Arguments.of("café 학생", 2),
                Arguments.of("'one' -two-", 2),
                Arguments.of(null, 0));
    }

    @ParameterizedTest
    @MethodSource("tokens")
    void countsOriginalTokensWithVersionedContractionAndHyphenRules(String text, int expected) {
        assertThat(GrowthWordCounter.count(text)).isEqualTo(expected);
    }
}
