package com.ceos24.autedi.domain.generation.contract.v1_1;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 이미지 표시를 위한 수정 동작과 문구. position은 삽입 이외의 동작에서 null이다. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AiEdit(AiEditOperation operation, String text, AiEditPosition position) {}
