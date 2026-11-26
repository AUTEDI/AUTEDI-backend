package com.ceos24.autedi.domain.student.entity;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "students", indexes = @Index(name = "idx_students_academy_name_id", columnList = "academy_id,name,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id", nullable = false, foreignKey = @ForeignKey(name = "fk_students_academy"))
    private Academy academy;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    // 학년 허용값은 후속 API 정책에서 정한다. 지금은 특정 학교급/학년 enum으로 제한하지 않는다.
    @Column(name = "grade", length = 50, nullable = false)
    private String grade;

    @Column(name = "class_name", length = 100)
    private String className;

    @Column(name = "student_phone", length = 20)
    private String studentPhone;

    @Column(name = "guardian_phone", length = 20)
    private String guardianPhone;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Builder
    public Student(
            Academy academy, String name, String grade, String className, String studentPhone, String guardianPhone) {
        if (academy == null) {
            throw new IllegalArgumentException("학생의 소속 기관은 필수입니다.");
        }
        this.academy = academy;
        this.name = requiredText(name, 50, "학생 이름");
        this.grade = requiredText(grade, 50, "학년");
        this.className = optionalText(className, 100, "수강반");
        this.studentPhone = optionalText(studentPhone, 20, "학생 연락처");
        this.guardianPhone = optionalText(guardianPhone, 20, "보호자 연락처");
    }

    /** 모든 값을 검증한 뒤 함께 반영한다. 이름과 소속 기관은 기본 정보 수정 대상이 아니다. */
    public void updateBasicInfo(String grade, String className, String studentPhone, String guardianPhone) {
        String checkedGrade = requiredText(grade, 50, "학년");
        String checkedClassName = optionalText(className, 100, "수강반");
        String checkedStudentPhone = optionalText(studentPhone, 20, "학생 연락처");
        String checkedGuardianPhone = optionalText(guardianPhone, 20, "보호자 연락처");
        this.grade = checkedGrade;
        this.className = checkedClassName;
        this.studentPhone = checkedStudentPhone;
        this.guardianPhone = checkedGuardianPhone;
    }

    /** 아직 저장되지 않은 서로 다른 기관을 id=null이라는 이유로 같은 기관으로 취급하지 않는다. */
    public boolean isInAcademy(Academy candidate) {
        return candidate != null
                && (academy == candidate
                        || (academy.getId() != null && academy.getId().equals(candidate.getId())));
    }

    private static String requiredText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "은(는) 필수입니다.");
        }
        String stripped = value.strip();
        if (stripped.length() > maximumLength) {
            throw new IllegalArgumentException(field + "의 저장 길이를 초과했습니다.");
        }
        return stripped;
    }

    private static String optionalText(String value, int maximumLength, String field) {
        if (value != null && value.length() > maximumLength) {
            throw new IllegalArgumentException(field + "의 저장 길이를 초과했습니다.");
        }
        return value;
    }
}
