package com.ceos24.autedi.domain.comment.entity;

import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "comments", indexes = @Index(name = "idx_comments_correction", columnList = "correction_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worksheet_id", nullable = false)
    private Worksheet worksheet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private WorksheetPage page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "highlight_type", length = 20, nullable = false)
    private HighlightType highlightType;

    @Enumerated(EnumType.STRING)
    @Column(name = "color", length = 20, nullable = false)
    private CommentColor color;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regions", columnDefinition = "json", nullable = false)
    private List<RegionPayload> regions;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", length = 20, nullable = false)
    @ColumnDefault("'GENERAL'")
    private CommentFeedbackType feedbackType = CommentFeedbackType.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "area", length = 50)
    private CorrectionArea area;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "issue_kinds", columnDefinition = "json")
    private List<AiIssueKind> issueKinds;

    // 같은 학습지의 활성 카드인지 서비스에서 검사한다. 운영 SQL의 FK로 물리 삭제도 방지한다.
    @Column(name = "correction_id")
    private Long correctionId;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Comment(
            Worksheet worksheet,
            WorksheetPage page,
            User user,
            HighlightType highlightType,
            CommentColor color,
            List<RegionPayload> regions,
            String content) {
        this.worksheet = worksheet;
        this.page = page;
        this.user = user;
        this.highlightType = highlightType;
        this.color = color != null ? color : CommentColor.BLUE;
        this.regions = regions;
        this.content = content;
        this.version = 1;
    }

    public void updateContent(String content) {
        if (content != null) {
            this.content = content;
        }
    }

    public void updateContent(String content, CommentColor color) {
        if (content != null) {
            this.content = content;
        }
        if (color != null) {
            this.color = color;
        }
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public CommentClassification getClassification() {
        return new CommentClassification(feedbackType, area, issueKinds, correctionId);
    }

    public void changeClassification(CommentClassification classification) {
        if (classification == null) throw new IllegalArgumentException("코멘트 분류가 필요합니다.");
        this.feedbackType = classification.type();
        this.area = classification.area();
        this.issueKinds = classification.issueKinds();
        this.correctionId = classification.correctionId();
    }

    public void restoreSavedEdit(String content, CommentColor color, LocalDateTime deletedAt) {
        restoreSavedEdit(content, color, deletedAt, CommentClassification.general());
    }

    public void restoreSavedEdit(
            String content, CommentColor color, LocalDateTime deletedAt, CommentClassification classification) {
        if (content == null || content.isBlank() || color == null) {
            throw new IllegalArgumentException("유효한 저장 코멘트가 필요합니다.");
        }
        this.content = content;
        this.color = color;
        this.deletedAt = deletedAt;
        changeClassification(classification);
        touch();
    }

    public String getEtag() {
        return "\"com_" + id + "-v" + (version != null ? version : 1) + "\"";
    }
}
