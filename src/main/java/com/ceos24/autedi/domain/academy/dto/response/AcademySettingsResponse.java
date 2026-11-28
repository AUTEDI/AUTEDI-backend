package com.ceos24.autedi.domain.academy.dto.response;

import com.ceos24.autedi.domain.academy.entity.AcademyClass;
import com.ceos24.autedi.domain.academy.entity.AcademyGrade;
import java.util.List;

public record AcademySettingsResponse(Long academyId, List<GradeOption> grades, List<ClassOption> classes) {
    public record GradeOption(Long gradeId, String name, AcademyGrade.Group group, boolean enabled) {
        public static GradeOption from(AcademyGrade grade) {
            return new GradeOption(grade.getId(), grade.getName(), grade.getGroup(), grade.isEnabled());
        }
    }

    public record ClassOption(Long classId, String name) {
        public static ClassOption from(AcademyClass schoolClass) {
            return new ClassOption(schoolClass.getId(), schoolClass.getName());
        }
    }
}
