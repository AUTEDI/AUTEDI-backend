package com.ceos24.autedi.domain.academy.service;

import com.ceos24.autedi.domain.academy.dto.response.AcademySettingsResponse;
import com.ceos24.autedi.domain.academy.dto.response.AcademySettingsResponse.*;
import com.ceos24.autedi.domain.academy.entity.*;
import com.ceos24.autedi.domain.academy.repository.*;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AcademySettingsService {
    private final AcademyRepository academies;
    private final AcademyGradeRepository grades;
    private final AcademyClassRepository classes;
    private final StudentRepository students;

    /** 설정 변경과 학생 배정을 같은 학원 잠금으로 직렬화한다. 첫 접근에서만 기존 문자열을 연결한다. */
    public Academy prepare(Long academyId) {
        Academy academy =
                academies.findLockedById(academyId).orElseThrow(() -> new CustomException(ErrorCode.ACADEMY_NOT_FOUND));
        if (academy.isSettingsInitialized()) return academy;
        for (int level = 1; level <= 6; level++) addDefault(academy, "초" + level, AcademyGrade.Group.ELEMENTARY);
        for (int level = 1; level <= 3; level++) addDefault(academy, "중" + level, AcademyGrade.Group.MIDDLE);
        // 학생 본문·저장된 첨삭 JSON은 읽지 않고, 서로 다른 학년/반 문자열만 조회한다.
        for (String name : students.findLegacyGrades(academyId)) {
            AcademyGrade grade = grades.findByAcademyIdAndName(academyId, name.strip())
                    .orElseGet(() -> grades.save(new AcademyGrade(academy, name, AcademyGrade.Group.CUSTOM, true)));
            grade.changeEnabled(true);
            students.linkLegacyGrade(academyId, name, grade);
        }
        for (String name : students.findLegacyClasses(academyId)) {
            if (name.isBlank()) continue;
            AcademyClass schoolClass = classes.findByAcademyIdAndName(academyId, name.strip())
                    .orElseGet(() -> classes.save(new AcademyClass(academy, name)));
            students.linkLegacyClass(academyId, name, schoolClass);
        }
        academy.markSettingsInitialized();
        return academy;
    }

    private void addDefault(Academy academy, String name, AcademyGrade.Group group) {
        if (grades.findByAcademyIdAndName(academy.getId(), name).isEmpty())
            grades.save(new AcademyGrade(academy, name, group, false));
    }

    public AcademySettingsResponse get(Long academyId) {
        prepare(academyId);
        return response(academyId);
    }

    public AcademySettingsResponse selectGrades(Long academyId, List<Long> ids) {
        prepare(academyId);
        if (new HashSet<>(ids).size() != ids.size())
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "학년 ID는 중복될 수 없습니다.");
        var options = grades.findByAcademyIdOrderByIdAsc(academyId);
        var available = new HashSet<>(options.stream().map(AcademyGrade::getId).toList());
        if (!available.containsAll(ids)) throw new CustomException(ErrorCode.ACADEMY_GRADE_NOT_FOUND);
        var selected = new HashSet<>(ids);
        options.forEach(grade -> grade.changeEnabled(selected.contains(grade.getId())));
        return response(academyId);
    }

    public GradeOption createGrade(Long academyId, String name) {
        Academy academy = prepare(academyId);
        if (grades.findByAcademyIdAndName(academyId, name).isPresent())
            throw new CustomException(ErrorCode.ACADEMY_GRADE_DUPLICATE);
        return GradeOption.from(grades.save(new AcademyGrade(academy, name, AcademyGrade.Group.CUSTOM, true)));
    }

    public ClassOption createClass(Long academyId, String name) {
        Academy academy = prepare(academyId);
        if (classes.findByAcademyIdAndName(academyId, name).isPresent())
            throw new CustomException(ErrorCode.ACADEMY_CLASS_DUPLICATE);
        return ClassOption.from(classes.save(new AcademyClass(academy, name)));
    }

    public ClassOption renameClass(Long academyId, Long classId, String name) {
        prepare(academyId);
        AcademyClass schoolClass = requireClass(academyId, classId);
        var duplicate = classes.findByAcademyIdAndName(academyId, name);
        if (duplicate.isPresent() && !duplicate.get().getId().equals(classId))
            throw new CustomException(ErrorCode.ACADEMY_CLASS_DUPLICATE);
        schoolClass.rename(name);
        return ClassOption.from(schoolClass);
    }

    public void deleteClass(Long academyId, Long classId) {
        prepare(academyId);
        AcademyClass schoolClass = requireClass(academyId, classId);
        if (students.existsBySchoolClassId(classId)) throw new CustomException(ErrorCode.ACADEMY_CLASS_IN_USE);
        classes.delete(schoolClass);
        classes.flush();
    }

    @Transactional(readOnly = true)
    public AcademyGrade requireGrade(Long academyId, Long gradeId) {
        return grades.findByIdAndAcademyId(gradeId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACADEMY_GRADE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public AcademyClass requireClass(Long academyId, Long classId) {
        return classes.findByIdAndAcademyId(classId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACADEMY_CLASS_NOT_FOUND));
    }

    private AcademySettingsResponse response(Long academyId) {
        return new AcademySettingsResponse(
                academyId,
                grades.findByAcademyIdOrderByIdAsc(academyId).stream()
                        .map(GradeOption::from)
                        .toList(),
                classes.findByAcademyIdOrderByIdAsc(academyId).stream()
                        .map(ClassOption::from)
                        .toList());
    }
}
