package com.ceos24.autedi.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호 정책: 8~20자이며 영문, 숫자, 특수문자(공백이 아닌 영문·숫자 외 문자)를 각각 1개 이상 포함한다.
 *
 * <p>null은 통과시킨다. 필수 여부는 {@code @NotBlank}가 담당한다.
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자여야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
