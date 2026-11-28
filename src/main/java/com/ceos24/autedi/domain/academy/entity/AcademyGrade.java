package com.ceos24.autedi.domain.academy.entity;

import com.ceos24.autedi.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "academy_grades",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_academy_grades_name",
                        columnNames = {"academy_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyGrade extends BaseTimeEntity {
    public enum Group {
        ELEMENTARY,
        MIDDLE,
        CUSTOM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id", nullable = false, foreignKey = @ForeignKey(name = "fk_academy_grades_academy"))
    private Academy academy;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "grade_group", nullable = false, length = 20)
    private Group group;

    @Column(nullable = false)
    private boolean enabled;

    public AcademyGrade(Academy academy, String name, Group group, boolean enabled) {
        if (academy == null || name == null || name.isBlank() || name.strip().length() > 50 || group == null)
            throw new IllegalArgumentException("유효한 학원·학년 이름·구분이 필요합니다.");
        this.academy = academy;
        this.name = name.strip();
        this.group = group;
        this.enabled = enabled;
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
