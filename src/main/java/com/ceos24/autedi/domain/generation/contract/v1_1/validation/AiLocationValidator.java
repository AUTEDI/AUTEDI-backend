package com.ceos24.autedi.domain.generation.contract.v1_1.validation;

import com.ceos24.autedi.domain.generation.contract.v1_1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiAnchor;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionItem;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiCorrectionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 파싱한 응답의 원문·좌표만 검사한다. 네트워크·DB·레벨·개수 정책을 실행하지 않는다. */
public final class AiLocationValidator {
    public List<AiLocationValidationResult> validate(AiCorrectionResponse response, AiLocationContext context) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(context, "context");
        List<AiLocationValidationResult> checked = new ArrayList<>();
        for (int index = 0; index < response.items().size(); index++) {
            checked.add(validateItem(index, response.items().get(index), context));
        }
        return List.copyOf(checked);
    }

    private static AiLocationValidationResult validateItem(
            int index, AiCorrectionItem item, AiLocationContext context) {
        List<AiValidationIssue> issues = new ArrayList<>();
        List<AiResolvedAnchor> resolved = resolveAnchors(item, context.transcriptionLines(), issues);
        String originalText = resolved.isEmpty()
                ? null
                : item.anchors().stream().map(AiAnchor::original).collect(Collectors.joining("\n"));
        List<List<Double>> boxes = new ArrayList<>();
        for (int boxIndex = 0; boxIndex < item.targetBoxes().size(); boxIndex++) {
            List<Double> box = item.targetBoxes().get(boxIndex);
            if (validBox(box)) {
                boxes.add(box);
            } else {
                issues.add(new AiValidationIssue(
                        AiValidationCode.INVALID_TARGET_BOX,
                        "target_boxes[" + boxIndex + "]",
                        "유한한 0~1 좌표와 left < right, top < bottom을 만족하지 않는 박스를 제외합니다."));
            }
        }
        if (resolved.isEmpty() && !boxes.isEmpty()) {
            boxes.clear();
            issues.add(new AiValidationIssue(
                    AiValidationCode.BOX_WITHOUT_ANCHORS, "target_boxes", "전체 원문 연결이 확인되지 않아 표시 좌표를 제공하지 않습니다."));
        }
        if (!resolved.isEmpty() && boxes.isEmpty()) {
            issues.add(new AiValidationIssue(
                    AiValidationCode.NO_VALID_TARGET_BOXES, "target_boxes", "원문은 확인됐지만 표시할 유효 좌표가 없습니다."));
        }
        if (item.locationUncertain()) {
            issues.add(new AiValidationIssue(
                    AiValidationCode.MODEL_LOCATION_UNCERTAIN, "location_uncertain", "모델이 이미지 위치를 불확실하게 판단했습니다."));
        } else if (item.targetBoxes().isEmpty()) {
            issues.add(new AiValidationIssue(
                    AiValidationCode.LOCATION_FLAG_INCONSISTENT,
                    "location_uncertain",
                    "원본 좌표가 비어 있지만 모델의 위치 불확실 표시는 false입니다."));
        }
        return new AiLocationValidationResult(context.pageNumber(), index, item, originalText, resolved, boxes, issues);
    }

    private static List<AiResolvedAnchor> resolveAnchors(
            AiCorrectionItem item, List<String> lines, List<AiValidationIssue> issues) {
        if (item.anchors().isEmpty()) {
            if (item.action() != AiAction.REVIEW) {
                issues.add(
                        new AiValidationIssue(AiValidationCode.ANCHORS_REQUIRED, "anchors", "일반 첨삭은 연결할 원문이 필요합니다."));
            }
            return List.of();
        }
        List<AiResolvedAnchor> resolved = new ArrayList<>();
        for (int index = 0; index < item.anchors().size(); index++) {
            AiAnchor anchor = item.anchors().get(index);
            String line =
                    anchor.lineId() >= 1 && anchor.lineId() <= lines.size() ? lines.get(anchor.lineId() - 1) : null;
            int start = findOccurrence(line, anchor.original(), anchor.occurrence());
            if (start < 0) {
                issues.add(new AiValidationIssue(
                        AiValidationCode.ANCHOR_NOT_FOUND,
                        "anchors[" + index + "]",
                        "지정한 줄·원문·등장 순서가 확정 전사문과 일치하지 않습니다."));
                continue;
            }
            int end = start + anchor.original().length();
            if (!codePointBoundary(line, start) || !codePointBoundary(line, end)) {
                issues.add(new AiValidationIssue(
                        AiValidationCode.ANCHOR_SPLITS_SURROGATE_PAIR,
                        "anchors[" + index + "].original",
                        "원문 범위가 UTF-16 서로게이트 쌍의 중간을 자릅니다."));
                continue;
            }
            resolved.add(new AiResolvedAnchor(anchor.lineId(), start, end));
        }
        // 한 구간이라도 실패하면 확인된 일부만 교체·표시할 수 없다.
        if (resolved.size() != item.anchors().size()) return List.of();
        if (resolved.size() == 1) return List.copyOf(resolved);

        boolean continuous = true;
        int firstLine =
                resolved.stream().mapToInt(AiResolvedAnchor::lineId).min().orElseThrow();
        int lastLine =
                resolved.stream().mapToInt(AiResolvedAnchor::lineId).max().orElseThrow();
        if (lines.subList(firstLine - 1, lastLine).stream().anyMatch(String::isBlank)) {
            continuous = false;
            issues.add(new AiValidationIssue(
                    AiValidationCode.PARAGRAPH_BOUNDARY, "anchors", "빈 줄이나 공백 줄을 넘는 문단 병합은 허용하지 않습니다."));
        }
        for (int index = 0; index < resolved.size(); index++) {
            AiResolvedAnchor anchor = resolved.get(index);
            if (index > 0 && anchor.lineId() != (long) resolved.get(index - 1).lineId() + 1) {
                continuous = false;
                issues.add(new AiValidationIssue(
                        AiValidationCode.ANCHORS_NOT_CONTIGUOUS,
                        "anchors[" + index + "]",
                        "원문 구간은 중복·역순·건너뛰기 없이 연속된 전사 줄 순서여야 합니다."));
            }
            if (index > 0 && anchor.start() != 0
                    || index < resolved.size() - 1
                            && anchor.end() != lines.get(anchor.lineId() - 1).length()) {
                continuous = false;
                issues.add(new AiValidationIssue(
                        AiValidationCode.ANCHOR_BOUNDARY_MISMATCH,
                        "anchors[" + index + "].original",
                        "첫 줄은 줄 끝까지, 마지막 줄은 줄 처음부터, 중간 줄은 전체를 포함해야 합니다."));
            }
        }
        return continuous ? List.copyOf(resolved) : List.of();
    }

    private static int findOccurrence(String line, String original, int occurrence) {
        if (line == null
                || original == null
                || original.isEmpty()
                || original.contains("\n")
                || original.contains("\r")
                || occurrence < 1
                || occurrence > line.length() / original.length()) return -1;
        int start = -1;
        int from = 0;
        for (int index = 0; index < occurrence; index++) {
            start = line.indexOf(original, from);
            if (start < 0) return -1;
            from = start + original.length();
        }
        return start;
    }

    private static boolean codePointBoundary(String line, int offset) {
        return offset == 0
                || offset == line.length()
                || !Character.isHighSurrogate(line.charAt(offset - 1))
                || !Character.isLowSurrogate(line.charAt(offset));
    }

    private static boolean validBox(List<Double> box) {
        if (box.size() != 4) return false;
        for (Double coordinate : box) {
            if (!Double.isFinite(coordinate) || coordinate < 0 || coordinate > 1) return false;
        }
        return box.get(0) < box.get(2) && box.get(1) < box.get(3);
    }
}
