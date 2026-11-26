package com.ceos24.autedi.domain.teacher.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 선생님 목록의 상태. 목록 화면의 필터(활동 중·초대 대기·퇴사)와 같은 값이다. */
@Schema(description = "선생님 목록 상태. ACTIVE=활동 중(퇴사가 아닌 가입 계정), INVITED=초대 대기, RESIGNED=퇴사")
public enum TeacherListStatus {
    ACTIVE,
    INVITED,
    RESIGNED
}
