package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionItem;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiEdit;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiEditOperation;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiEditPosition;
import java.util.ArrayList;
import java.util.List;

/** 원문·좌표 검사 뒤의 필드 조합·수정 동작 검사. AI·DB·레벨·개수 정책을 실행하지 않는다. */
public final class AiEditValidator {
    public List<AiEditValidationResult> validate(List<AiLocationValidationResult> locations) {
        List<AiEditValidationResult> checked = new ArrayList<>();
        for (AiLocationValidationResult location : List.copyOf(locations)) {
            List<AiValidationIssue> issues = new ArrayList<>();
            AiCorrectionItem item = location.original();
            check(
                    item.reason() != null && !item.reason().isBlank(),
                    AiValidationCode.INVALID_ACTION_FIELDS,
                    "reason",
                    "설명 또는 확인 사유가 필요합니다.",
                    issues);
            if (item.action() == null) {
                check(false, AiValidationCode.INVALID_ACTION_FIELDS, "action", "제안 동작이 필요합니다.", issues);
            } else {
                switch (item.action()) {
                    case REPLACE -> checkReplacement(location, issues);
                    case GUIDANCE -> checkGuidance(item, issues);
                    case REVIEW -> checkReview(item, issues);
                }
            }
            checked.add(new AiEditValidationResult(location, issues));
        }
        return List.copyOf(checked);
    }

    private static void checkReplacement(AiLocationValidationResult location, List<AiValidationIssue> issues) {
        AiCorrectionItem item = location.original();
        check(
                item.feedbackType() != null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "feedback_type",
                "원문 수정의 유형이 필요합니다.",
                issues);
        check(item.area() != null, AiValidationCode.INVALID_ACTION_FIELDS, "area", "원문 수정의 대표 영역이 필요합니다.", issues);
        check(
                item.replacement() != null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "replacement",
                "원문 수정의 교체문은 null일 수 없습니다.",
                issues);
        check(
                item.guidance() == null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "guidance",
                "원문 수정의 guidance는 null이어야 합니다.",
                issues);
        check(
                item.revisionScope() != null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "revision_scope",
                "원문 수정의 범위가 필요합니다.",
                issues);
        check(item.edit() != null, AiValidationCode.INVALID_ACTION_FIELDS, "edit", "원문 수정의 세부 동작이 필요합니다.", issues);
        AiEdit edit = item.edit();
        if (edit == null) return;
        check(
                edit.operation() != null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "edit.operation",
                "세부 수정 동작이 필요합니다.",
                issues);
        check(edit.text() != null, AiValidationCode.INVALID_EDIT_TEXT, "edit.text", "표시 문구는 null일 수 없습니다.", issues);
        if (edit.operation() == null || edit.text() == null || item.replacement() == null) return;

        check(
                edit.operation() == AiEditOperation.INSERT ? edit.position() != null : edit.position() == null,
                AiValidationCode.INVALID_EDIT_POSITION,
                "edit.position",
                "삽입에는 BEFORE 또는 AFTER가 필요하고, 그 외 동작의 위치는 null이어야 합니다.",
                issues);
        switch (edit.operation()) {
            case INSERT -> checkInsertion(location, issues);
            case DELETE -> {
                check(
                        item.replacement().isEmpty(),
                        AiValidationCode.INVALID_REPLACEMENT,
                        "replacement",
                        "삭제의 교체문은 빈 문자열이어야 합니다.",
                        issues);
                check(
                        edit.text().isEmpty(),
                        AiValidationCode.INVALID_EDIT_TEXT,
                        "edit.text",
                        "삭제에는 새 표시 문구가 없어야 합니다.",
                        issues);
            }
            case REPLACE, RESTRUCTURE -> {
                check(
                        !item.replacement().isBlank(),
                        AiValidationCode.INVALID_REPLACEMENT,
                        "replacement",
                        "교체·문장 재구성의 교체문은 비어 있을 수 없습니다.",
                        issues);
                check(
                        item.replacement().equals(edit.text()),
                        AiValidationCode.EDIT_REPLACEMENT_MISMATCH,
                        "edit.text",
                        "교체문과 이미지에 적을 문구가 일치해야 합니다.",
                        issues);
                if (location.sourceResolved()) {
                    check(
                            !item.replacement().equals(location.originalText()),
                            AiValidationCode.NO_OP_REPLACEMENT,
                            "replacement",
                            "원문과 완전히 같은 교체문은 실제 변경이 아닙니다.",
                            issues);
                }
            }
        }
    }

    private static void checkInsertion(AiLocationValidationResult location, List<AiValidationIssue> issues) {
        AiCorrectionItem item = location.original();
        AiEdit edit = item.edit();
        String text = edit.text();
        boolean usableText = !text.isBlank() && !boundaryWhitespace(text);
        check(usableText, AiValidationCode.INVALID_EDIT_TEXT, "edit.text", "삽입 문구는 비어 있거나 앞뒤 공백을 포함할 수 없습니다.", issues);
        boolean usableReplacement = !item.replacement().isBlank();
        check(
                usableReplacement,
                AiValidationCode.INVALID_REPLACEMENT,
                "replacement",
                "삽입의 교체문에는 기존 원문과 추가 문구가 필요합니다.",
                issues);
        if (!usableText || !usableReplacement || edit.position() == null || !location.sourceResolved()) return;

        String source = location.originalText();
        String replacement = item.replacement();
        boolean before = edit.position() == AiEditPosition.BEFORE;
        boolean sourceKept = before ? replacement.endsWith(source) : replacement.startsWith(source);
        check(
                sourceKept,
                AiValidationCode.INSERT_SOURCE_CHANGED,
                "replacement",
                "지정된 삽입 경계에서 기존 원문을 그대로 유지해야 합니다.",
                issues);
        if (!sourceKept) return;
        String inserted = before
                ? replacement.substring(0, replacement.length() - source.length())
                : replacement.substring(source.length());
        boolean sameText = inserted.equals(text);
        // 원문에 없는 단어 경계 공백 하나만 추가 허용한다. 탭·줄바꿈·중복 공백은 보정하지 않는다.
        boolean sourceHasSpace = whitespace(before ? source.codePointAt(0) : source.codePointBefore(source.length()));
        boolean withBoundarySpace = !sourceHasSpace && inserted.equals(before ? text + " " : " " + text);
        check(
                sameText || withBoundarySpace,
                AiValidationCode.INSERT_TEXT_MISMATCH,
                "edit.text",
                "실제 추가분이 표시 문구 및 허용된 경계 공백과 일치하지 않습니다.",
                issues);
    }

    private static void checkGuidance(AiCorrectionItem item, List<AiValidationIssue> issues) {
        check(
                item.feedbackType() != null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "feedback_type",
                "내용 보완 안내의 유형이 필요합니다.",
                issues);
        check(
                item.area() == AiArea.CONTENT_SUMMARY,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "area",
                "내용 보완 안내의 영역은 CONTENT_SUMMARY여야 합니다.",
                issues);
        check(
                item.replacement() == null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "replacement",
                "내용 보완 안내는 원문 교체문을 가질 수 없습니다.",
                issues);
        check(
                item.guidance() != null && !item.guidance().isBlank(),
                AiValidationCode.INVALID_ACTION_FIELDS,
                "guidance",
                "비어 있지 않은 내용 보완 안내가 필요합니다.",
                issues);
        check(
                item.revisionScope() == null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "revision_scope",
                "내용 보완 안내의 수정 범위는 null이어야 합니다.",
                issues);
        check(
                item.edit() == null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "edit",
                "내용 보완 안내는 원문 수정 동작을 가질 수 없습니다.",
                issues);
    }

    private static void checkReview(AiCorrectionItem item, List<AiValidationIssue> issues) {
        check(
                item.feedbackType() == null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "feedback_type",
                "확인 항목의 유형은 null이어야 합니다.",
                issues);
        check(
                item.area() == null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "area",
                "확인 항목의 대표 영역은 null이어야 합니다.",
                issues);
        check(
                item.replacement() == null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "replacement",
                "확인 항목의 교체문은 null이어야 합니다.",
                issues);
        check(
                item.guidance() == null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "guidance",
                "확인 항목의 안내문은 null이어야 합니다.",
                issues);
        check(
                item.revisionScope() == null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "revision_scope",
                "확인 항목의 수정 범위는 null이어야 합니다.",
                issues);
        check(
                item.edit() == null,
                AiValidationCode.INVALID_ACTION_FIELDS,
                "edit",
                "확인 항목의 수정 동작은 null이어야 합니다.",
                issues);
        if (item.anchors().isEmpty()) {
            check(
                    item.targetBoxes().isEmpty(),
                    AiValidationCode.INVALID_ACTION_FIELDS,
                    "target_boxes",
                    "원문 연결 없는 확인 항목의 좌표는 비어 있어야 합니다.",
                    issues);
            check(
                    item.locationUncertain(),
                    AiValidationCode.INVALID_ACTION_FIELDS,
                    "location_uncertain",
                    "원문 연결 없는 확인 항목은 위치 불확실 표시가 필요합니다.",
                    issues);
        }
    }

    private static boolean boundaryWhitespace(String text) {
        return !text.isEmpty() && (whitespace(text.codePointAt(0)) || whitespace(text.codePointBefore(text.length())));
    }

    private static boolean whitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static void check(
            boolean valid, AiValidationCode code, String field, String message, List<AiValidationIssue> issues) {
        if (!valid) issues.add(new AiValidationIssue(code, field, message));
    }
}
