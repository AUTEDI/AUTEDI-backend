package com.ceos24.autedi.domain.student.exception;

import com.ceos24.autedi.domain.student.dto.response.StudentDuplicateResponse;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class DuplicateStudentException extends CustomException {

    private final StudentDuplicateResponse detail;

    public DuplicateStudentException(StudentDuplicateResponse detail) {
        super(ErrorCode.STUDENT_DUPLICATE_CANDIDATES);
        this.detail = detail;
    }
}
