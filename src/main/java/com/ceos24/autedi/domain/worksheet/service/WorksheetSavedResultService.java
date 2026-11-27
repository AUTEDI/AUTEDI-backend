package com.ceos24.autedi.domain.worksheet.service;

import com.ceos24.autedi.domain.comment.entity.Comment;
import com.ceos24.autedi.domain.comment.repository.CommentRepository;
import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.correction.service.CorrectionService;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetSavedResultResponse;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSavedResult;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSavedResultRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorksheetSavedResultService {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};
    private final WorksheetReviewAccessService access;
    private final WorksheetRepository worksheets;
    private final WorksheetSavedResultRepository savedResults;
    private final WorksheetGenerationRepository generations;
    private final CorrectionRepository corrections;
    private final CommentRepository comments;
    private final CorrectionService reviewService;

    @Transactional
    public WorksheetSavedResultResponse saveDraft(Long userId, Long academyId, Long worksheetId, String ifMatch) {
        Worksheet worksheet = access.lockForOwner(userId, academyId, worksheetId);
        validateIfMatch(ifMatch, worksheet.getEtag(), false);
        if (worksheet.getTitle() == null || worksheet.getTitle().isBlank() || worksheet.getStudent() == null) {
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE, "AI 생성 전에 제목과 학생을 지정해 주세요.");
        }
        LocalDateTime savedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        worksheet.saveDraft(worksheet.getUser(), savedAt);
        worksheets.flush();
        ReviewResponse review = reviewService.getReview(academyId, worksheetId);
        var snapshot = new WorksheetSavedSnapshot(
                review,
                corrections.findAllByWorksheetIdOrderByIdAsc(worksheetId).stream()
                        .map(WorksheetSavedSnapshot.CorrectionEdit::from)
                        .toList(),
                comments.findAllByWorksheetIdOrderByIdAsc(worksheetId).stream()
                        .map(WorksheetSavedSnapshot.CommentEdit::from)
                        .toList());
        WorksheetSavedResult saved =
                savedResults.findById(worksheetId).orElseGet(() -> new WorksheetSavedResult(worksheet));
        var generation = generations
                .findFirstByWorksheetIdOrderByCreatedAtDescIdDesc(worksheetId)
                .orElse(null);
        saved.replace(generation, worksheet.getUser(), savedAt, JSON.convertValue(snapshot, OBJECT));
        saved = savedResults.saveAndFlush(saved);
        return response(saved, decode(saved));
    }

    public WorksheetSavedResultResponse getSavedResult(Long userId, Long academyId, Long worksheetId) {
        Worksheet worksheet = access.readForOwner(userId, academyId, worksheetId);
        WorksheetSavedResult saved = findSaved(worksheetId);
        validateSource(worksheet, saved);
        return response(saved, decode(saved));
    }

    @Transactional
    public ReviewResponse resume(Long userId, Long academyId, Long worksheetId, String ifMatch) {
        Worksheet worksheet = access.lockForOwner(userId, academyId, worksheetId);
        validateIfMatch(ifMatch, worksheet.getEtag(), true);
        WorksheetSavedResult saved = findSaved(worksheetId);
        if (worksheet.getSaveStatus() != WorksheetSaveStatus.TEMP_SAVED) {
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE);
        }
        validateSource(worksheet, saved);
        WorksheetSavedSnapshot snapshot = decode(saved);
        var metadata = snapshot.review().worksheet();
        if (metadata == null
                || !Objects.equals(metadata.id(), worksheetId)
                || !Objects.equals(metadata.title(), worksheet.getTitle())
                || metadata.student() == null
                || worksheet.getStudent() == null
                || !Objects.equals(
                        metadata.student().studentId(), worksheet.getStudent().getId())) {
            throw new CustomException(ErrorCode.SAVED_RESULT_CONFLICT);
        }
        Map<Long, Correction> currentCorrections = corrections.findAllByWorksheetIdOrderByIdAsc(worksheetId).stream()
                .collect(Collectors.toMap(Correction::getId, Function.identity()));
        Map<Long, Comment> currentComments = comments.findAllByWorksheetIdOrderByIdAsc(worksheetId).stream()
                .collect(Collectors.toMap(Comment::getId, Function.identity()));
        var correctionIds = snapshot.corrections().stream()
                .map(WorksheetSavedSnapshot.CorrectionEdit::id)
                .toList();
        var commentIds = snapshot.comments().stream()
                .map(WorksheetSavedSnapshot.CommentEdit::id)
                .toList();
        if (new HashSet<>(correctionIds).size() != correctionIds.size()
                || new HashSet<>(commentIds).size() != commentIds.size()
                || !currentCorrections.keySet().containsAll(correctionIds)
                || !currentComments.keySet().containsAll(commentIds)) {
            throw new CustomException(ErrorCode.SAVED_RESULT_CONFLICT);
        }
        try {
            snapshot.corrections().forEach(edit -> edit.restore(currentCorrections.remove(edit.id())));
            snapshot.comments().forEach(edit -> edit.restore(currentComments.remove(edit.id())));
        } catch (IllegalArgumentException e) {
            // 이미 복원한 행도 같은 트랜잭션에서 전부 롤백한다.
            throw new CustomException(ErrorCode.SAVED_RESULT_UNREADABLE);
        }
        currentCorrections.values().stream()
                .filter(c -> c.getStatus() != CorrectionStatus.DELETED)
                .forEach(Correction::delete);
        currentComments.values().stream().filter(c -> !c.isDeleted()).forEach(Comment::delete);
        worksheet.markReviewEdited();
        worksheets.flush();
        return reviewService.getReview(academyId, worksheetId);
    }

    private WorksheetSavedResult findSaved(Long worksheetId) {
        return savedResults
                .findById(worksheetId)
                .orElseThrow(() -> new CustomException(ErrorCode.SAVED_RESULT_NOT_FOUND));
    }

    private void validateSource(Worksheet worksheet, WorksheetSavedResult saved) {
        Long latestId = generations
                .findFirstByWorksheetIdOrderByCreatedAtDescIdDesc(worksheet.getId())
                .map(g -> g.getId())
                .orElse(null);
        Long savedGenerationId = saved.getSourceGeneration() == null
                ? null
                : saved.getSourceGeneration().getId();
        if (!Objects.equals(worksheet.getFileHash(), saved.getSourceFileHash())
                || !Objects.equals(latestId, savedGenerationId)) {
            throw new CustomException(ErrorCode.SAVED_RESULT_CONFLICT);
        }
    }

    private WorksheetSavedSnapshot decode(WorksheetSavedResult saved) {
        try {
            if (saved.getSchemaVersion() != 1) throw new IllegalArgumentException("지원하지 않는 저장본 버전");
            return JSON.convertValue(saved.getSnapshot(), WorksheetSavedSnapshot.class);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.SAVED_RESULT_UNREADABLE);
        }
    }

    private WorksheetSavedResultResponse response(WorksheetSavedResult saved, WorksheetSavedSnapshot snapshot) {
        return new WorksheetSavedResultResponse(
                saved.getWorksheetId(),
                saved.getRevision(),
                saved.getEtag(),
                saved.getSavedAt(),
                saved.getSavedBy().getId(),
                snapshot.review());
    }

    private void validateIfMatch(String ifMatch, String current, boolean required) {
        if (ifMatch == null && !required) return;
        if (ifMatch == null
                || !(ifMatch.trim().equals(current)
                        || !required && ifMatch.trim().equals("*"))) {
            throw new CustomException(ErrorCode.PRECONDITION_FAILED);
        }
    }
}
