package com.ceos24.autedi.domain.academy.entity;

import com.ceos24.autedi.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "academy_classes",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_academy_classes_name",
                        columnNames = {"academy_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyClass extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id", nullable = false, foreignKey = @ForeignKey(name = "fk_academy_classes_academy"))
    private Academy academy;

    @Column(nullable = false, length = 100)
    private String name;

    public AcademyClass(Academy academy, String name) {
        if (academy == null) throw new IllegalArgumentException("소속 학원은 필수입니다.");
        this.academy = academy;
        rename(name);
    }

    public void rename(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 100)
            throw new IllegalArgumentException("반 이름은 1~100자여야 합니다.");
        this.name = name.strip();
    }
}
