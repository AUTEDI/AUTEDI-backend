package com.ceos24.autedi.domain.correction.service;

import com.ceos24.autedi.domain.comment.dto.response.CommentResponse;
import com.ceos24.autedi.domain.comment.repository.CommentRepository;
import com.ceos24.autedi.domain.correction.dto.request.CorrectionUpdateRequest;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionListResponse;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionResponse;
import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.generation.contract.v1.AiAction;
import com.ceos24.autedi.domain.generation.dto.response.GenerationResponse;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.entity.WorksheetGenerationResult;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationResultRepository;
import com.ceos24.autedi.domain.generation.service.FixedResponseGenerationService;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewAreaSummaryResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewIssueResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewResultResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetDetailResponse;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSettingRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CorrectionService {

    private final WorksheetRepository worksheetRepository;
    private final WorksheetPageRepository worksheetPageRepository;
    private final WorksheetSettingRepository worksheetSettingRepository;
    private final WorksheetGenerationRepository worksheetGenerationRepository;
    private final CorrectionRepository correctionRepository;
    private final CommentRepository commentRepository;
    private final WorksheetGenerationResultRepository generationResults;

    public ReviewResponse getReview(Long academyId, Long worksheetId) {
        Worksheet worksheet = findWorksheetAndValidateReady(academyId, worksheetId);

        List<WorksheetPage> pages = worksheetPageRepository.findAllByWorksheetIdOrderByPageNumberAsc(worksheetId);
        List<WorksheetSetting> settings = worksheetSettingRepository.findAllByWorksheetId(worksheetId);
        WorksheetGeneration latestGen = worksheetGenerationRepository
                .findFirstByWorksheetIdOrderByCreatedAtDescIdDesc(worksheetId)
                .orElse(null);

        Long latestGenId = latestGen != null ? latestGen.getId() : null;
        WorksheetDetailResponse worksheetDetail = WorksheetDetailResponse.of(worksheet, pages, settings, latestGenId);
        GenerationResponse generationResponse = latestGen != null ? GenerationResponse.from(latestGen) : null;

        List<WorksheetGenerationResult> archived = latestGenId == null
                ? List.of()
                : generationResults.findAllByGenerationIdInOrderByPagePageNumberAsc(List.of(latestGenId));
        Map<Long, String> sources = sources(archived);
        List<CorrectionResponse> corrections =
                correctionRepository.findAllByWorksheetIdAndStatus(worksheetId, CorrectionStatus.ACTIVE).stream()
                        .map(correction -> response(correction, sources))
                        .toList();

        List<CommentResponse> comments = commentRepository.findAllActiveByWorksheet(worksheetId, null).stream()
                .map(CommentResponse::from)
                .toList();

        List<ReviewIssueResponse> reviewIssues = new ArrayList<>();
        List<ReviewAreaSummaryResponse> summary = new ArrayList<>();
        List<ReviewResultResponse> resultInfo = new ArrayList<>();
        for (WorksheetGenerationResult entity : archived) {
            var saved = entity.snapshot();
            var transcript = saved.transcription();
            resultInfo.add(new ReviewResultResponse(
                    saved.id(),
                    saved.generationId(),
                    transcript.pageId(),
                    transcript.id(),
                    sources.get(saved.generationId()),
                    saved.metadata()));
            if (saved.validationResult() == null) continue;
            for (var issue : saved.validationResult().reviewIssues()) {
                reviewIssues.add(new ReviewIssueResponse(
                        saved.id(),
                        saved.generationId(),
                        transcript.id(),
                        transcript.pageId(),
                        issue.pageNumber(),
                        issue.candidateIndex(),
                        issue.original(),
                        issue.resolvedAnchor(),
                        issue.validBoxes(),
                        issue.issues(),
                        issue.requiresReview()));
            }
            for (var area : saved.validationResult().summary()) {
                int active = (int) corrections.stream()
                        .filter(card -> saved.generationId().equals(card.generationId())
                                && card.area().name().equals(area.area().name())
                                && card.anchor() != null
                                && card.anchor().transcriptionId().equals(transcript.id()))
                        .count();
                summary.add(new ReviewAreaSummaryResponse(
                        saved.generationId(),
                        transcript.pageId(),
                        transcript.pageNumber(),
                        area.area(),
                        area.level(),
                        area.recommendedCount(),
                        area.maximumCount(),
                        area.displayedCount(),
                        active,
                        area.omittedCount()));
            }
        }
        return new ReviewResponse(
                worksheetDetail, generationResponse, corrections, comments, reviewIssues, summary, resultInfo);
    }

    public CorrectionListResponse getCorrections(
            Long academyId, Long worksheetId, String statusParam, Long pageId, CorrectionArea area) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        CorrectionStatus filterStatus = parseStatus(statusParam);

        var entities = correctionRepository.findAllByFilter(worksheetId, filterStatus, pageId, area);
        var sources = sources(generationResults.findAllByGenerationIdInOrderByPagePageNumberAsc(entities.stream()
                .map(card -> card.getGeneration().getId())
                .distinct()
                .toList()));
        List<CorrectionResponse> items =
                entities.stream().map(card -> response(card, sources)).toList();

        return CorrectionListResponse.of(items);
    }

    public CorrectionResponse getCorrection(Long academyId, Long worksheetId, Long correctionId) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        Correction correction = findCorrectionByIdAndWorksheetId(correctionId, worksheetId);
        return response(correction);
    }

    @Transactional
    public CorrectionResponse updateCorrection(
            Long academyId, Long worksheetId, Long correctionId, String ifMatch, CorrectionUpdateRequest request) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        Correction correction = findCorrectionByIdAndWorksheetId(correctionId, worksheetId);

        validateIfMatch(ifMatch, correction.getEtag());

        if (correction.getStatus() == CorrectionStatus.DELETED) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_DELETED_CORRECTION);
        }

        String content = request.content();
        if (content == null || correction.getAction() != AiAction.REPLACE && content.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "내용 보완 안내와 기존 첨삭 내용은 비어 있을 수 없습니다.");
        }
        correction.editContent(correction.getAction() == AiAction.REPLACE ? content : content.trim());
        correctionRepository.flush();
        log.info("첨삭 내용 수정 완료: correctionId={}, worksheetId={}", correctionId, worksheetId);

        return response(correction);
    }

    @Transactional
    public String deleteCorrection(Long academyId, Long worksheetId, Long correctionId, String ifMatch) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        Correction correction = findCorrectionByIdAndWorksheetId(correctionId, worksheetId);

        validateIfMatch(ifMatch, correction.getEtag());

        if (correction.getStatus() == CorrectionStatus.DELETED) {
            log.info("이미 삭제된 첨삭 항목 (204 처리): correctionId={}", correctionId);
            return correction.getEtag();
        }

        correction.delete();
        correctionRepository.flush();
        log.info("첨삭 항목 논리 삭제 완료: correctionId={}, worksheetId={}", correctionId, worksheetId);

        return correction.getEtag();
    }

    @Transactional
    public CorrectionResponse restoreCorrection(Long academyId, Long worksheetId, Long correctionId, String ifMatch) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        Correction correction = findCorrectionByIdAndWorksheetId(correctionId, worksheetId);

        validateIfMatch(ifMatch, correction.getEtag());

        if (correction.getStatus() == CorrectionStatus.ACTIVE) {
            log.info("이미 활성 상태인 첨삭 항목 (200 처리): correctionId={}", correctionId);
            return response(correction);
        }

        correction.restore();
        correctionRepository.flush();
        log.info("첨삭 항목 복원 완료: correctionId={}, worksheetId={}", correctionId, worksheetId);

        return response(correction);
    }

    private CorrectionResponse response(Correction correction) {
        return response(
                correction,
                sources(generationResults.findAllByGenerationIdInOrderByPagePageNumberAsc(
                        List.of(correction.getGeneration().getId()))));
    }

    private static CorrectionResponse response(Correction correction, Map<Long, String> sources) {
        return CorrectionResponse.of(
                correction,
                correction.getEtag(),
                sources.getOrDefault(correction.getGeneration().getId(), "AI"));
    }

    private static Map<Long, String> sources(List<WorksheetGenerationResult> results) {
        Map<Long, String> sources = new HashMap<>();
        for (var result : results)
            sources.put(
                    result.getGeneration().getId(),
                    FixedResponseGenerationService.MODEL.equals(result.getModel()) ? "FIXED" : "AI");
        return sources;
    }

    private Worksheet findWorksheetAndValidateReady(Long academyId, Long worksheetId) {
        Worksheet worksheet = worksheetRepository
                .findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));

        if (worksheet.getStatus() != WorksheetStatus.READY) {
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE, "학습지가 검토 준비 완료(READY) 상태가 아닙니다.");
        }
        return worksheet;
    }

    private Correction findCorrectionByIdAndWorksheetId(Long correctionId, Long worksheetId) {
        Correction correction = correctionRepository
                .findById(correctionId)
                .orElseThrow(() -> new CustomException(ErrorCode.CORRECTION_NOT_FOUND));

        if (!correction.getWorksheet().getId().equals(worksheetId)) {
            throw new CustomException(ErrorCode.CORRECTION_NOT_FOUND);
        }
        return correction;
    }

    private CorrectionStatus parseStatus(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return CorrectionStatus.ACTIVE;
        }
        if ("ALL".equalsIgnoreCase(statusParam.trim())) {
            return null;
        }
        try {
            return CorrectionStatus.valueOf(statusParam.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 status 필터 값입니다: " + statusParam);
        }
    }

    private void validateIfMatch(String rawIfMatch, String currentEtag) {
        if (rawIfMatch == null || rawIfMatch.isBlank()) {
            throw new CustomException(ErrorCode.PRECONDITION_FAILED);
        }
        String cleanIfMatch = rawIfMatch.trim().replace("\"", "");
        String cleanCurrent = currentEtag.trim().replace("\"", "");
        if (!cleanIfMatch.equals(cleanCurrent)) {
            throw new CustomException(ErrorCode.PRECONDITION_FAILED);
        }
    }
}
