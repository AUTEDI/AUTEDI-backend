package com.ceos24.autedi.domain.insight;

import static org.assertj.core.api.Assertions.*;

import com.ceos24.autedi.domain.correction.entity.FeedbackType;
import com.ceos24.autedi.domain.insight.dto.InsightDocument.Evidence;
import com.ceos24.autedi.domain.insight.service.InsightContract;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InsightContractTest {
    private final List<Evidence> evidence = List.of(
            e("a", 1, FeedbackType.CORRECTION),
            e("b", 2, FeedbackType.CORRECTION),
            e("same", 1, FeedbackType.CORRECTION),
            e("suggestion", 3, FeedbackType.SUGGESTION));

    @Test
    void acceptsGroundedItemsAndCoachingAndPreservesEvidence() {
        var result = InsightContract.parse(body("ERROR_PATTERN", "[\"a\",\"b\"]"), evidence);
        assertThat(result.insights()).hasSize(1);
        assertThat(result.coachingPoints()).hasSize(1);
        assertThat(result.evidence()).containsExactly(evidence.get(0), evidence.get(1));
    }

    @Test
    void improvementCanUseOneGroundedSuggestionWithoutClaimingRepetition() {
        assertThat(InsightContract.parse(body("IMPROVEMENT", "[\"suggestion\"]"), evidence)
                        .insights())
                .hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[\"a\"]",
                "[\"a\",\"same\"]",
                "[\"a\",\"suggestion\"]",
                "[\"a\",\"unknown\"]",
                "[\"a\",\"a\"]",
                "[]",
                "null",
                "[null]"
            })
    void rejectsUnsupportedOrUnjustifiedRepeatedErrorEvidence(String ids) {
        assertThatThrownBy(() -> InsightContract.parse(body("ERROR_PATTERN", ids), evidence))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo("INSIGHT_004"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "[]",
                "{\"insights\":[],\"coachingPoints\":[]}",
                "{\"insights\":[],\"coachingPoints\":null}",
                "{\"insights\":[],\"insights\":[],\"coachingPoints\":[]}"
            })
    void rejectsInvalidShapes(String raw) {
        assertThatThrownBy(() -> InsightContract.parse(raw, evidence)).isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsTrailingDataUnknownFieldsAreaMismatchAndOversizedText() {
        String valid = body("ERROR_PATTERN", "[\"a\",\"b\"]");
        for (String raw : List.of(
                valid + " {}",
                valid.replace("\"title\":", "\"extra\":true,\"title\":"),
                valid.replace("GRAMMAR_SPELLING", "CONTENT_SUMMARY"),
                valid.replace("\"문법\"", "\"" + "x".repeat(61) + "\""),
                valid.replace("\"설명\"", "\"" + "x".repeat(301) + "\"")))
            assertThatThrownBy(() -> InsightContract.parse(raw, evidence)).isInstanceOf(CustomException.class);
    }

    private String body(String kind, String ids) {
        return "{\"insights\":[{\"title\":\"문법\",\"description\":\"설명\",\"area\":\"GRAMMAR_SPELLING\",\"kind\":\""
                + kind + "\",\"evidenceIds\":" + ids + "}],"
                + "\"coachingPoints\":[{\"content\":\"연습\",\"evidenceIds\":[\"a\"]}]}";
    }

    private Evidence e(String id, long worksheet, FeedbackType type) {
        return new Evidence(
                id,
                worksheet,
                1L,
                "AI",
                type,
                CorrectionArea.GRAMMAR_SPELLING,
                LocalDateTime.of(2026, 11, 1, 0, 0),
                "He go",
                "He goes",
                null);
    }
}
