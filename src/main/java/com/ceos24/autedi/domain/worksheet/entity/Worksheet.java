package com.ceos24.autedi.domain.worksheet.entity;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
        name = "worksheets",
        indexes = {
            @Index(name = "idx_worksheets_student_id", columnList = "student_id"),
            @Index(name = "idx_worksheets_last_saved_by", columnList = "last_saved_by")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Worksheet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id", nullable = false)
    private Academy academy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private WorksheetStatus status;

    // 업로드 후 설정하는 값이므로, 기존 자료와 최초 업로드는 null을 허용한다.
    @Column(name = "title", length = 255)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", foreignKey = @ForeignKey(name = "fk_worksheets_student"))
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "save_status", length = 20, nullable = false)
    @ColumnDefault("'UNSAVED'")
    private WorksheetSaveStatus saveStatus;

    @Column(name = "first_saved_at")
    private LocalDateTime firstSavedAt;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_saved_by", foreignKey = @ForeignKey(name = "fk_worksheets_last_saved_by"))
    private User lastSavedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    @Column(name = "file_url", length = 500, nullable = false)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", length = 10, nullable = false)
    private FileType fileType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_hash", length = 64, nullable = false)
    private String fileHash;

    @Column(name = "page_count", nullable = false)
    private Integer pageCount;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Builder
    public Worksheet(
            Academy academy,
            User user,
            String originalFilename,
            String fileUrl,
            FileType fileType,
            Long fileSize,
            String fileHash,
            Integer pageCount) {
        this.academy = academy;
        this.user = user;
        this.status = WorksheetStatus.DRAFT;
        this.saveStatus = WorksheetSaveStatus.UNSAVED;
        this.originalFilename = originalFilename;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.pageCount = pageCount != null ? pageCount : 1;
        this.version = 1;
    }

    public boolean canModifyOrReplace() {
        return this.status == WorksheetStatus.DRAFT || this.status == WorksheetStatus.FAILED;
    }

    /** 메타데이터의 기본 불변 조건만 확인한다. 요청자 권한과 AI 상태별 허용 범위는 후속 서비스에서 정한다. */
    public void updateMetadata(String title, Student student) {
        if (deletedAt != null || saveStatus == WorksheetSaveStatus.COMPLETED) {
            throw new IllegalStateException("삭제되었거나 최종 저장된 학습지의 정보는 변경할 수 없습니다.");
        }
        if (title == null || title.isBlank() || title.strip().length() > 255) {
            throw new IllegalArgumentException("학습지 제목은 비어 있지 않은 255자 이하여야 합니다.");
        }
        if (student == null || !student.isInAcademy(academy)) {
            throw new IllegalArgumentException("학습지와 같은 기관의 학생을 지정해야 합니다.");
        }
        this.title = title.strip();
        this.student = student;
    }

    public void replaceFile(
            String originalFilename, String fileUrl, FileType fileType, Long fileSize, String fileHash) {
        this.originalFilename = originalFilename;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.status = WorksheetStatus.DRAFT;
    }

    public void updateStatus(WorksheetStatus status) {
        this.status = status;
    }

    public String getEtag() {
        return "\"ws_" + this.id + "-v" + (this.version != null ? this.version : 1) + "\"";
    }
}
