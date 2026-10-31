package com.ceos24.autedi.domain.correction.entity;

import com.ceos24.autedi.domain.generation.contract.v1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1.AiFeedbackType;
import com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationIssue;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.global.entity.BaseTimeEntity;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "corrections",
        check =
                @CheckConstraint(
                        name = "CHK_corrections_contract_v1",
                        constraint =
                                "(action IS NULL AND feedback_type IS NULL AND issue_kinds IS NULL AND anchor IS NULL "
                                        + "AND location_uncertain IS NULL AND warnings IS NULL AND requires_review IS NULL) OR "
                                        + "(action IS NOT NULL AND action IN ('REPLACE','GUIDANCE') AND feedback_type IS NOT NULL "
                                        + "AND feedback_type IN ('CORRECTION','SUGGESTION') AND issue_kinds IS NOT NULL AND anchor IS NOT NULL "
                                        + "AND location_uncertain IS NOT NULL AND warnings IS NOT NULL AND requires_review IS NOT NULL "
                                        + "AND (action <> 'GUIDANCE' OR area = 'CONTENT_SUMMARY'))"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Correction extends BaseTimeEntity {

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
    @JoinColumn(name = "generation_id", nullable = false)
    private WorksheetGeneration generation;

    @Enumerated(EnumType.STRING)
    @Column(name = "area", length = 50, nullable = false, updatable = false)
    private CorrectionArea area;

    @Column(name = "original_text", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String originalText;

    @Column(name = "ai_initial_content", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String aiInitialContent;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "explanation", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regions", columnDefinition = "json", nullable = false, updatable = false)
    private List<RegionPayload> regions;

    // 기존 행은 7개 필드가 모두 null인 미분류 상태로만 허용한다. v1 생성 경로는 전부 채운다.
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, updatable = false)
    private AiAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", length = 20, updatable = false)
    private AiFeedbackType feedbackType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "issue_kinds", columnDefinition = "json", updatable = false)
    private List<AiIssueKind> issueKinds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "anchor", columnDefinition = "json", updatable = false)
    private CorrectionAnchor anchor;

    @Column(name = "location_uncertain", updatable = false)
    private Boolean locationUncertain;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", columnDefinition = "json", updatable = false)
    private List<AiValidationIssue> warnings;

    @Column(name = "requires_review", updatable = false)
    private Boolean requiresReview;

    @Column(name = "is_edited", nullable = false)
    private Boolean isEdited;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CorrectionStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Correction(
            Worksheet worksheet,
            WorksheetPage page,
            WorksheetGeneration generation,
            CorrectionArea area,
            String originalText,
            String aiInitialContent,
            String content,
            String explanation,
            List<RegionPayload> regions,
            AiAction action,
            AiFeedbackType feedbackType,
            List<AiIssueKind> issueKinds,
            CorrectionAnchor anchor,
            Boolean locationUncertain,
            List<AiValidationIssue> warnings,
            Boolean requiresReview) {
        this.worksheet = worksheet;
        this.page = page;
        this.generation = generation;
        this.area = area;
        this.originalText = originalText;
        this.aiInitialContent = aiInitialContent;
        this.content = content != null ? content : aiInitialContent;
        this.explanation = explanation;
        this.regions = List.copyOf(regions);
        this.action = action;
        this.feedbackType = feedbackType;
        this.issueKinds = issueKinds == null ? null : List.copyOf(issueKinds);
        this.anchor = anchor;
        this.locationUncertain = locationUncertain;
        this.warnings = warnings == null ? null : List.copyOf(warnings);
        this.requiresReview = requiresReview;
        validateV1Fields();
        this.isEdited = false;
        this.status = CorrectionStatus.ACTIVE;
        this.version = 1;
    }

    public void editContent(String newContent) {
        if (newContent == null || action == AiAction.GUIDANCE && newContent.isBlank()) {
            throw new IllegalArgumentException("내용 안내는 비어 있을 수 없으며 수정 내용은 null일 수 없습니다.");
        }
        this.content = newContent;
        this.isEdited = true;
    }

    public void delete() {
        this.status = CorrectionStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.status = CorrectionStatus.ACTIVE;
        this.deletedAt = null;
    }

    public String getEtag() {
        return "\"cor_" + id + "-v" + (version != null ? version : 1) + "\"";
    }

    private void validateV1Fields() {
        boolean legacy = action == null
                && feedbackType == null
                && issueKinds == null
                && anchor == null
                && locationUncertain == null
                && warnings == null
                && requiresReview == null;
        if (legacy) return;
        if (action == null
                || action == AiAction.REVIEW
                || feedbackType == null
                || issueKinds == null
                || anchor == null
                || locationUncertain == null
                || warnings == null
                || requiresReview == null) {
            throw new IllegalArgumentException("v1 Correction은 REVIEW가 아니며 7개 계약 필드가 모두 필요합니다.");
        }
        Set<AiIssueKind> allowed = Set.of(
                AiIssueKind.GRAMMAR,
                AiIssueKind.SPELLING,
                AiIssueKind.PUNCTUATION,
                AiIssueKind.VOCABULARY,
                AiIssueKind.SENTENCE_STRUCTURE,
                AiIssueKind.CONTENT);
        if (issueKinds.isEmpty()
                || new HashSet<>(issueKinds).size() != issueKinds.size()
                || !allowed.containsAll(issueKinds)) {
            throw new IllegalArgumentException("Correction에는 중복 없는 일반 첨삭 문제 종류가 필요합니다.");
        }
        if (originalText == null || originalText.isEmpty() || anchor.end() - anchor.start() != originalText.length()) {
            throw new IllegalArgumentException("원문과 UTF-16 anchor 길이가 일치해야 합니다.");
        }
        if (action == AiAction.GUIDANCE
                && (area != CorrectionArea.CONTENT_SUMMARY
                        || aiInitialContent == null
                        || aiInitialContent.isBlank()
                        || content == null
                        || content.isBlank())) {
            throw new IllegalArgumentException("GUIDANCE는 CONTENT_SUMMARY의 비어 있지 않은 안내여야 합니다.");
        }
        if (requiresReview != (locationUncertain || !warnings.isEmpty())) {
            throw new IllegalArgumentException("requiresReview는 모델 위치 불확실성과 서버 경고를 반영해야 합니다.");
        }
    }
}
