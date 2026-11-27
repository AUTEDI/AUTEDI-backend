package com.ceos24.autedi.domain.student.service;

import java.util.regex.Pattern;

/** v1: 문자·숫자 토큰. 단어 내부의 apostrophe와 ASCII hyphen은 연결하고 그 외 구두점은 경계다. */
final class GrowthWordCounter {
    private static final Pattern WORD =
            Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*(?:['’\\-][\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*)*");

    private GrowthWordCounter() {}

    static int count(String text) {
        return text == null ? 0 : Math.toIntExact(WORD.matcher(text).results().count());
    }
}
