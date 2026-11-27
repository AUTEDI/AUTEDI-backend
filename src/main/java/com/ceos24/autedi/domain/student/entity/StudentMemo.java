package com.ceos24.autedi.domain.student.entity;

import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.global.entity.BaseCreatedTimeEntity;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 교사가 학생에 대해 남기는 메모. 작성자와 작성 시각을 함께 보존하며, 수정·삭제 정책은 확정 전이라 변경 기능이 없다. */
@Entity
@Table(
        name = "student_memos",
        indexes = @Index(name = "idx_student_memos_student_created", columnList = "student_id,created_at,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentMemo extends BaseCreatedTimeEntity {

    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_student_memos_student"))
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_student_memos_author"))
    private User author;

    @Column(name = "content", length = MAX_CONTENT_LENGTH, nullable = false)
    private String content;

    @Builder
    public StudentMemo(Student student, User author, String content) {
        if (student == null) {
            throw new IllegalArgumentException("메모를 남길 학생은 필수입니다.");
        }
        if (author == null) {
            throw new IllegalArgumentException("메모 작성자는 필수입니다.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("메모 내용은 비어 있을 수 없습니다.");
        }
        String stripped = content.strip();
        if (stripped.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("메모 내용은 " + MAX_CONTENT_LENGTH + "자 이하여야 합니다.");
        }
        this.student = student;
        this.author = author;
        this.content = stripped;
    }
}
