package com.ceos24.autedi.domain.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.student.entity.Student;
import org.junit.jupiter.api.Test;

class StudentModelTest {

    @Test
    void studentRequiresAcademyNameAndGrade() {
        Academy academy = Academy.builder().name("기관").build();

        assertThatThrownBy(() -> Student.builder().name("학생").grade("초등 3").build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Student.builder()
                        .academy(academy)
                        .name("  ")
                        .grade("초등 3")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Student.builder()
                        .academy(academy)
                        .name("학생")
                        .grade(null)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionalInformationDoesNotRestrictAnOtherwiseValidStudent() {
        Student student = Student.builder()
                .academy(Academy.builder().name("기관").build())
                .name("  김학생  ")
                .grade("  학년 정책 미정  ")
                .build();

        assertThat(student.getName()).isEqualTo("김학생");
        assertThat(student.getGrade()).isEqualTo("학년 정책 미정");
        assertThat(student.getClassName()).isNull();
        assertThat(student.getStudentPhone()).isNull();
        assertThat(student.getGuardianPhone()).isNull();
    }

    @Test
    void twoUnsavedAcademiesAreNotTheSameTenantBecauseBothIdsAreNull() {
        Academy first = Academy.builder().name("첫 기관").build();
        Academy second = Academy.builder().name("다른 기관").build();
        Student student =
                Student.builder().academy(first).name("학생").grade("초등 3").build();

        assertThat(student.isInAcademy(first)).isTrue();
        assertThat(student.isInAcademy(second)).isFalse();
        assertThat(student.isInAcademy(null)).isFalse();
    }

    @Test
    void basicInformationCanChangeAndOptionalValuesCanBeClearedWithoutChangingIdentity() {
        Academy academy = Academy.builder().name("기관").build();
        Student student = Student.builder()
                .academy(academy)
                .name("김학생")
                .grade("초등 3")
                .className("오후반")
                .studentPhone("01012345678")
                .guardianPhone("01087654321")
                .build();
        student.updateBasicInfo("  초등 4  ", null, null, "02-123-4567");
        assertThat(student.getName()).isEqualTo("김학생");
        assertThat(student.getAcademy()).isSameAs(academy);
        assertThat(student.getGrade()).isEqualTo("초등 4");
        assertThat(student.getClassName()).isNull();
        assertThat(student.getStudentPhone()).isNull();
        assertThat(student.getGuardianPhone()).isEqualTo("02-123-4567");
    }

    @Test
    void invalidBasicInformationDoesNotPartiallyChangeOtherFields() {
        Student student = Student.builder()
                .academy(Academy.builder().name("기관").build())
                .name("김학생")
                .grade("초등 3")
                .className("오후반")
                .studentPhone("01012345678")
                .guardianPhone("01087654321")
                .build();
        assertThatThrownBy(() -> student.updateBasicInfo("초등 4", "오전반", null, "1".repeat(21)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(student.getGrade()).isEqualTo("초등 3");
        assertThat(student.getClassName()).isEqualTo("오후반");
        assertThat(student.getStudentPhone()).isEqualTo("01012345678");
        assertThat(student.getGuardianPhone()).isEqualTo("01087654321");
    }
}
