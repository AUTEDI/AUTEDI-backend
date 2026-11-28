package com.ceos24.autedi.domain.student.entity;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.AcademyClass;
import com.ceos24.autedi.domain.academy.entity.AcademyGrade;
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
@Table(
        name = "students",
        indexes = {
            @Index(name = "idx_students_academy_name_id", columnList = "academy_id,name,id"),
            @Index(name = "idx_students_grade_id", columnList = "grade_id"),
            @Index(name = "idx_students_class_id", columnList = "class_id")
        })
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

    // 기존 데이터 이관 및 과거 스키마 호환용 표시값. 신규 API는 학원 설정 ID로 선택한다.
    @Column(name = "grade", length = 50, nullable = false)
    private String grade;

    @Column(name = "class_name", length = 100)
    private String className;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", foreignKey = @ForeignKey(name = "fk_students_grade"))
    private AcademyGrade gradeOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", foreignKey = @ForeignKey(name = "fk_students_class"))
    private AcademyClass schoolClass;

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
        this.gradeOption = null;
        this.schoolClass = null;
    }

    public void select(AcademyGrade grade, AcademyClass schoolClass) {
        if (grade == null
                || !isInAcademy(grade.getAcademy())
                || (schoolClass != null && !isInAcademy(schoolClass.getAcademy())))
            throw new IllegalArgumentException("같은 학원의 학년·반만 선택할 수 있습니다.");
        this.gradeOption = grade;
        this.schoolClass = schoolClass;
        this.grade = grade.getName();
        this.className = schoolClass == null ? null : schoolClass.getName();
    }

    public String getGrade() {
        return gradeOption == null ? grade : gradeOption.getName();
    }

    public String getClassName() {
        return schoolClass == null ? className : schoolClass.getName();
    }

    public Long getGradeId() {
        return gradeOption == null ? null : gradeOption.getId();
    }

    public Long getClassId() {
        return schoolClass == null ? null : schoolClass.getId();
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
