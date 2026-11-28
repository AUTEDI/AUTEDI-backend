package com.ceos24.autedi.domain.worksheet.service;

import com.ceos24.autedi.domain.comment.entity.Comment;
import com.ceos24.autedi.domain.comment.entity.CommentClassification;
import com.ceos24.autedi.domain.comment.entity.CommentColor;
import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.entity.CorrectionV11Details;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 공개 검토 표현과 삭제 행을 포함한 복원용 편집 상태. DB 식별자·버전·AI 원본은 복원하지 않는다. */
public record WorksheetSavedSnapshot(
        ReviewResponse review, List<CorrectionEdit> corrections, List<CommentEdit> comments) {
    public WorksheetSavedSnapshot {
        Objects.requireNonNull(review);
        corrections = List.copyOf(corrections);
        comments = List.copyOf(comments);
    }

    public record CorrectionEdit(
            Long id,
            String content,
            List<RegionPayload> regions,
            Boolean edited,
            Boolean applied,
            CorrectionStatus status,
            LocalDateTime deletedAt,
            CorrectionV11Details details) {
        public CorrectionEdit {
            Objects.requireNonNull(id);
            Objects.requireNonNull(content);
            regions = List.copyOf(regions);
            Objects.requireNonNull(edited);
            Objects.requireNonNull(applied);
            Objects.requireNonNull(status);
            if ((status == CorrectionStatus.DELETED) != (deletedAt != null))
                throw new IllegalArgumentException("삭제 상태 불일치");
        }

        public static CorrectionEdit from(Correction c) {
            return new CorrectionEdit(
                    c.getId(),
                    c.getContent(),
                    c.getRegions(),
                    c.getIsEdited(),
                    c.getIsApplied(),
                    c.getStatus(),
                    c.getDeletedAt(),
                    c.getV11Details());
        }

        public void restore(Correction c) {
            c.restoreSavedEdit(content, regions, edited, applied, status, deletedAt, details);
        }
    }

    public record CommentEdit(
            Long id,
            String content,
            CommentColor color,
            LocalDateTime deletedAt,
            CommentClassification classification) {
        public CommentEdit {
            Objects.requireNonNull(id);
            Objects.requireNonNull(content);
            Objects.requireNonNull(color);
            classification = classification == null ? CommentClassification.general() : classification;
        }

        public static CommentEdit from(Comment c) {
            return new CommentEdit(c.getId(), c.getContent(), c.getColor(), c.getDeletedAt(), c.getClassification());
        }

        public void restore(Comment c) {
            c.restoreSavedEdit(content, color, deletedAt, classification);
        }
    }
}
