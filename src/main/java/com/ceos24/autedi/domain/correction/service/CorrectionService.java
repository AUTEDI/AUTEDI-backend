package com.ceos24.autedi.domain.correction.service;

import com.ceos24.autedi.domain.comment.dto.response.CommentResponse;
import com.ceos24.autedi.domain.comment.repository.CommentRepository;
import com.ceos24.autedi.domain.correction.dto.request.CorrectionUpdateRequest;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionListResponse;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionResponse;
import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.entity.FeedbackType;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
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
import java.util.Objects;
import java.util.stream.Stream;
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

        var activeCards = correctionRepository.findAllByWorksheetIdAndStatus(worksheetId, CorrectionStatus.ACTIVE);
        var allResults = generationResults.findAllByGenerationIdInOrderByPagePageNumberAsc(Stream.concat(
                        Stream.ofNullable(latestGenId),
                        activeCards.stream().map(card -> card.getGeneration().getId()))
                .distinct()
                .toList());
        List<WorksheetGenerationResult> archived = allResults.stream()
                .filter(result ->
                        Objects.equals(latestGenId, result.getGeneration().getId()))
                .toList();
        var contexts = contexts(allResults);
        List<CorrectionResponse> corrections = activeCards.stream()
                .map(correction -> response(correction, contexts))
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
                    contexts.get(saved.generationId()).source(),
                    saved.metadata()));
            ReviewResultMapper.append(saved, corrections, reviewIssues, summary);
        }
        return new ReviewResponse(
                worksheetDetail, generationResponse, corrections, comments, reviewIssues, summary, resultInfo);
    }

    public CorrectionListResponse getCorrections(
            Long academyId, Long worksheetId, String statusParam, Long pageId, CorrectionArea area) {
        return getCorrections(academyId, worksheetId, statusParam, pageId, area, null, null);
    }

    public CorrectionListResponse getCorrections(
            Long academyId,
            Long worksheetId,
            String statusParam,
            Long pageId,
            CorrectionArea area,
            FeedbackType feedbackType) {
        return getCorrections(academyId, worksheetId, statusParam, pageId, area, feedbackType, null);
    }

    public CorrectionListResponse getCorrections(
            Long academyId,
            Long worksheetId,
            String statusParam,
            Long pageId,
            CorrectionArea area,
            FeedbackType feedbackType,
            Boolean isApplied) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        CorrectionStatus filterStatus = parseStatus(statusParam);

        var entities =
                correctionRepository.findAllByFilter(worksheetId, filterStatus, pageId, area, feedbackType, isApplied);
        var contexts = contexts(generationResults.findAllByGenerationIdInOrderByPagePageNumberAsc(entities.stream()
                .map(card -> card.getGeneration().getId())
                .distinct()
                .toList()));
        List<CorrectionResponse> items =
                entities.stream().map(card -> response(card, contexts)).toList();

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
        if (content == null && request.regions() == null && request.isApplied() == null)
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "수정할 내용·좌표·반영 여부 중 하나가 필요합니다.");
        if (content != null && correction.getAction() != AiAction.REPLACE && content.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "내용 보완 안내와 기존 첨삭 내용은 비어 있을 수 없습니다.");
        }
        List<RegionPayload> regions = request.regions() == null
                ? null
                : request.regions().stream()
                        .map(region ->
                                validatedRegion(region, correction.getPage().getId()))
                        .toList();
        correction.update(
                content == null || correction.getAction() == AiAction.REPLACE ? content : content.trim(),
                regions,
                request.isApplied());
        correctionRepository.flush();
        log.info("첨삭 내용 수정 완료: correctionId={}, worksheetId={}", correctionId, worksheetId);

        return response(correction);
    }

    @Transactional
    public CorrectionResponse toggleCorrectionApplied(
            Long academyId, Long worksheetId, Long correctionId, boolean applied) {
        return toggleCorrectionApplied(academyId, worksheetId, correctionId, null, applied);
    }

    @Transactional
    public CorrectionResponse toggleCorrectionApplied(
            Long academyId, Long worksheetId, Long correctionId, String ifMatch, boolean applied) {
        findWorksheetAndValidateReady(academyId, worksheetId);
        Correction correction = findCorrectionByIdAndWorksheetId(correctionId, worksheetId);
        // develop의 기존 토글 요청은 헤더 없이도 허용한다. 헤더가 있으면 충돌을 검사한다.
        if (ifMatch != null && !ifMatch.isBlank()) validateIfMatch(ifMatch, correction.getEtag());
        if (correction.getStatus() == CorrectionStatus.DELETED)
            throw new CustomException(ErrorCode.CANNOT_MODIFY_DELETED_CORRECTION);
        correction.updateApplied(applied);
        correctionRepository.flush();
        return response(correction);
    }

    private static RegionPayload validatedRegion(RegionPayload region, Long pageId) {
        if (region == null
                || region.pageId() != null && !region.pageId().equals(pageId)
                || region.x() == null
                || region.y() == null
                || region.width() == null
                || region.height() == null
                || !Double.isFinite(region.x())
                || !Double.isFinite(region.y())
                || !Double.isFinite(region.width())
                || !Double.isFinite(region.height())
                || region.x() < 0
                || region.y() < 0
                || region.width() <= 0
                || region.height() <= 0
                || region.x() + region.width() > 1 + 1e-12
                || region.y() + region.height() > 1 + 1e-12)
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "좌표는 해당 페이지의 0~1 정규화 영역이어야 합니다.");
        return new RegionPayload(pageId, region.x(), region.y(), region.width(), region.height(), region.polygon());
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
                contexts(generationResults.findAllByGenerationIdInOrderByPagePageNumberAsc(
                        List.of(correction.getGeneration().getId()))));
    }

    private static CorrectionResponse response(Correction correction, Map<Long, ResultContext> contexts) {
        var context = contexts.get(correction.getGeneration().getId());
        return CorrectionResponse.of(
                correction,
                correction.getEtag(),
                context == null ? "AI" : context.source(),
                context == null ? null : context.contractVersion());
    }

    private record ResultContext(String source, String contractVersion) {}

    private static Map<Long, ResultContext> contexts(List<WorksheetGenerationResult> results) {
        Map<Long, ResultContext> contexts = new HashMap<>();
        for (var result : results)
            contexts.put(
                    result.getGeneration().getId(),
                    new ResultContext(
                            FixedResponseGenerationService.MODEL.equals(result.getModel()) ? "FIXED" : "AI",
                            result.getContractVersion()));
        return contexts;
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
