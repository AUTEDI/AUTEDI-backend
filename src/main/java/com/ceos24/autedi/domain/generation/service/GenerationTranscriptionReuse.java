package com.ceos24.autedi.domain.generation.service;

import com.ceos24.autedi.domain.generation.client.GenerationClientProfiles;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationContext;
import com.ceos24.autedi.domain.generation.entity.GenerationClientMode;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.entity.WorksheetTranscription;
import com.ceos24.autedi.domain.generation.persistence.StoredTranscription;
import com.ceos24.autedi.domain.generation.repository.WorksheetTranscriptionRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.service.PreparedWorksheetImage;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
import com.ceos24.autedi.global.storage.LocalStorageService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 재시도 접수와 실행 직전에 같은 기준으로 불변 전사의 연결·무결성을 확인한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenerationTranscriptionReuse {
    private final WorksheetTranscriptionRepository transcriptions;
    private final WorksheetPageRepository pages;
    private final WorksheetService images;

    public Long findForRetry(WorksheetGeneration previous, GenerationClientMode mode) {
        Long academyId = previous.getWorksheet().getAcademy().getId();
        Long worksheetId = previous.getWorksheet().getId();
        var page = pages.findByWorksheetIdAndPageNumber(worksheetId, 1).orElse(null);
        if (page == null) return null;
        Long candidate = previous.getInputTranscriptionId();
        if (candidate == null)
            candidate = transcriptions
                    .findByGenerationIdAndPageId(previous.getId(), page.getId())
                    .map(WorksheetTranscription::getId)
                    .orElse(null);
        if (candidate == null) return null;
        var image = images.getPreparedImage(academyId, worksheetId, 1);
        return resolve(previous, mode, candidate, image) == null ? null : candidate;
    }

    public StoredTranscription resolve(
            WorksheetGeneration job, GenerationClientMode mode, Long id, PreparedWorksheetImage image) {
        if (id == null) return null;
        var saved = transcriptions
                .findByIdAndGenerationWorksheetAcademyId(
                        id, job.getWorksheet().getAcademy().getId())
                .orElse(null);
        if (saved == null) return null;
        var source = saved.getGeneration();
        if (!source.getWorksheet().getId().equals(job.getWorksheet().getId())
                || source.getClientMode() != mode
                || !saved.getPage().getId().equals(image.pageId())
                || !image.worksheetId().equals(job.getWorksheet().getId())
                || saved.getPageNumber() != image.pageNumber()
                || !Objects.equals(saved.getSourceFileHash(), job.getRequestFileHash())
                || !Objects.equals(job.getRequestFileHash(), job.getWorksheet().getFileHash())
                || !Objects.equals(saved.getSourceImageHash(), image.sha256())
                || saved.getImageWidth() != image.width()
                || saved.getImageHeight() != image.height()
                || !GenerationClientProfiles.model(mode).equals(saved.getModel())
                || !WorksheetTranscription.NORMALIZATION_VERSION.equals(saved.getNormalizationVersion())
                || !Objects.equals(
                        saved.getTextHash(),
                        LocalStorageService.sha256(saved.getText().getBytes(StandardCharsets.UTF_8)))) return null;
        try {
            new AiValidationContext(image.pageNumber(), saved.getLines(), Map.of());
        } catch (IllegalArgumentException e) {
            return null;
        }
        return saved.snapshot();
    }
}
