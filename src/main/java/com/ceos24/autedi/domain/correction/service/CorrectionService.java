package com.ceos24.autedi.domain.correction.service;

import com.ceos24.autedi.domain.comment.dto.response.CommentResponse;
import com.ceos24.autedi.domain.comment.repository.CommentRepository;
import com.ceos24.autedi.domain.correction.dto.request.CorrectionUpdateRequest;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionListResponse;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionResponse;
import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.generation.dto.response.GenerationResponse;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewResponse;
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
import java.util.List;
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

    public ReviewResponse getReview(Long academyId, Long worksheetId) {
        Worksheet worksheet = findWorksheetAndValidateReady(academyId, worksheetId);

        List<WorksheetPage> pages = worksheetPageRepository.findAllByWorksheetIdOrderByPageNumberAsc(worksheetId);
        List<WorksheetSetting> settings = worksheetSettingRepository.findAllByWorksheetId(worksheetId);
        WorksheetGeneration latestGen = worksheetGenerationRepository
                .findFirstByWorksheetIdOrderByCreatedAtDesc(worksheetId)
                .orElse(null);

        Long latestGenId = latestGen != null ? latestGen.getId() : null;
        WorksheetDetailResponse worksheetDetail = WorksheetDetailResponse.of(worksheet, pages, settings, latestGenId);
        GenerationResponse generationResponse = latestGen != null ? GenerationResponse.from(latestGen) : null;

        List<CorrectionResponse> corrections =
                correctionRepository.findAllByWorksheetIdAndStatus(worksheetId, CorrectionStatus.ACTIVE).stream()
                        .map(CorrectionResponse::from)
                        .toList();

        List<CommentResponse> comments = commentRepository.findAllActiveByWorksheet(worksheetId, null).stream()
                .map(CommentResponse::from)
                .toList();

        return new ReviewResponse(worksheetDetail, generationResponse, corrections, comments);
    }

    public CorrectionListResponse getCorrections(
            Long academyId, Long worksheetId, String statusParam, Long pageId, CorrectionArea area) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        CorrectionStatus filterStatus = parseStatus(statusParam);

        List<CorrectionResponse> items =
                correctionRepository.findAllByFilter(worksheetId, filterStatus, pageId, area).stream()
                        .map(CorrectionResponse::from)
                        .toList();

        return CorrectionListResponse.of(items);
    }

    public CorrectionResponse getCorrection(Long academyId, Long worksheetId, Long correctionId) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        Correction correction = findCorrectionByIdAndWorksheetId(correctionId, worksheetId);
        return CorrectionResponse.from(correction);
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

        correction.editContent(request.content().trim());
        log.info("첨삭 내용 수정 완료: correctionId={}, worksheetId={}", correctionId, worksheetId);

        String nextEtag = "\"cor_" + correction.getId() + "-v" + (correction.getVersion() + 1) + "\"";
        return CorrectionResponse.of(correction, nextEtag);
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
        log.info("첨삭 항목 논리 삭제 완료: correctionId={}, worksheetId={}", correctionId, worksheetId);

        return "\"cor_" + correction.getId() + "-v" + (correction.getVersion() + 1) + "\"";
    }

    @Transactional
    public CorrectionResponse restoreCorrection(Long academyId, Long worksheetId, Long correctionId, String ifMatch) {
        findWorksheetAndValidateReady(academyId, worksheetId);

        Correction correction = findCorrectionByIdAndWorksheetId(correctionId, worksheetId);

        validateIfMatch(ifMatch, correction.getEtag());

        if (correction.getStatus() == CorrectionStatus.ACTIVE) {
            log.info("이미 활성 상태인 첨삭 항목 (200 처리): correctionId={}", correctionId);
            return CorrectionResponse.from(correction);
        }

        correction.restore();
        log.info("첨삭 항목 복원 완료: correctionId={}, worksheetId={}", correctionId, worksheetId);

        String nextEtag = "\"cor_" + correction.getId() + "-v" + (correction.getVersion() + 1) + "\"";
        return CorrectionResponse.of(correction, nextEtag);
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
