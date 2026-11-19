package com.ceos24.autedi.global.logging;

/** 로그 MDC에 넣는 키. 값은 숫자 ID처럼 개인을 알아볼 수 없는 것만 넣는다. */
public final class MdcKeys {
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String ACADEMY_ID = "academyId";

    private MdcKeys() {}
}
