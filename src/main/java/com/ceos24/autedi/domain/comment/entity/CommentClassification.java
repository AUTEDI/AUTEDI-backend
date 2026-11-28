package com.ceos24.autedi.domain.comment.entity;

import com.ceos24.autedi.domain.correction.entity.FeedbackTaxonomy;
import com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "교사 코멘트 분류. 지정하면 전체 교체하며, 오류/제안에는 영역·종류, 보충 설명에는 correctionId가 필요합니다.")
public record CommentClassification(
        CommentFeedbackType type, CorrectionArea area, List<AiIssueKind> issueKinds, Long correctionId) {
    public CommentClassification {
        if (type == null) throw new IllegalArgumentException("코멘트 종류가 필요합니다.");
        area = FeedbackTaxonomy.canonical(area);
        if (type == CommentFeedbackType.CORRECTION || type == CommentFeedbackType.SUGGESTION) {
            if (area == null || !FeedbackTaxonomy.validKinds(issueKinds) || correctionId != null)
                throw new IllegalArgumentException("오류·제안에는 대표 영역과 중복 없는 일반 오류 종류가 필요합니다.");
            CorrectionArea selected = area;
            if (issueKinds.stream().noneMatch(kind -> FeedbackTaxonomy.areaOf(kind) == selected))
                throw new IllegalArgumentException("대표 영역에 해당하는 오류 종류가 필요합니다.");
        } else {
            if (area != null || issueKinds != null && !issueKinds.isEmpty())
                throw new IllegalArgumentException("일반 코멘트·보충 설명에는 오류 분류를 지정할 수 없습니다.");
            if (type == CommentFeedbackType.SUPPLEMENT
                    ? correctionId == null || correctionId <= 0
                    : correctionId != null) throw new IllegalArgumentException("보충 설명에만 대상 첨삭 ID를 지정해야 합니다.");
        }
        issueKinds = issueKinds == null ? List.of() : List.copyOf(issueKinds);
    }

    public static CommentClassification general() {
        return new CommentClassification(CommentFeedbackType.GENERAL, null, List.of(), null);
    }
}
