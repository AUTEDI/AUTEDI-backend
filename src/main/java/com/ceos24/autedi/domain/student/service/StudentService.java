package com.ceos24.autedi.domain.student.service;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.student.dto.request.StudentCreateRequest;
import com.ceos24.autedi.domain.student.dto.response.StudentResponse;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentService {

    private final StudentRepository studentRepository;
    private final AcademyRepository academyRepository;

    @Transactional
    public StudentResponse createStudent(Long academyId, StudentCreateRequest request) {
        Academy academy = academyRepository
                .findById(academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACADEMY_NOT_FOUND));

        Student student = Student.builder()
                .academy(academy)
                .name(request.name())
                .grade(request.grade())
                .className(request.className())
                .studentPhone(request.studentPhone())
                .guardianPhone(request.guardianPhone())
                .build();

        return StudentResponse.from(studentRepository.save(student));
    }

    public StudentResponse getStudent(Long academyId, Long studentId) {
        Student student = studentRepository
                .findByIdAndAcademyId(studentId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        return StudentResponse.from(student);
    }
}
