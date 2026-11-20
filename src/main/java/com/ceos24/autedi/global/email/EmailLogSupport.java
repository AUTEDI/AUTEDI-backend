package com.ceos24.autedi.global.email;

import java.util.Locale;
import org.springframework.core.NestedExceptionUtils;

/** 이메일 로그에 주소 대신 남길 값을 만든다. 같은 패키지의 발신기들만 쓴다. */
final class EmailLogSupport {

    private EmailLogSupport() {}

    /** 이메일 주소에서 도메인만 꺼낸다. 주소가 없거나 형식이 이상하면 "unknown". */
    static String domainOf(String email) {
        if (email == null) {
            return "unknown";
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return "unknown";
        }
        return email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    /** 가장 깊은 원인 예외의 클래스 이름. 원인이 없으면 예외 자신의 이름이다. */
    static String causeType(Throwable error) {
        return NestedExceptionUtils.getMostSpecificCause(error).getClass().getSimpleName();
    }
}
