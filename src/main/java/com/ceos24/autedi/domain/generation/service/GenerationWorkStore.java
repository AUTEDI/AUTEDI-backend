package com.ceos24.autedi.domain.generation.service;

import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.generation.client.CorrectionDraft;
import com.ceos24.autedi.domain.generation.client.GenerationClientProfiles;
import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationResult;
import com.ceos24.autedi.domain.generation.entity.GenerationClientMode;
import com.ceos24.autedi.domain.generation.entity.GenerationStage;
import com.ceos24.autedi.domain.generation.entity.GenerationStatus;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.persistence.StoredTranscription;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionLevel;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.service.PreparedWorksheetImage;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 단계별 짧은 트랜잭션. 이 클래스 안에서는 클라이언트를 호출하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenerationWorkStore {
    private final WorksheetGenerationRepository generations;
    private final WorksheetRepository worksheets;
    private final WorksheetPageRepository pages;
    private final CorrectionRepository corrections;
    private final GenerationResultStorageService archives;
    private final GenerationTranscriptionReuse transcriptionReuse;

    public record Work(
            Long id,
            Long academyId,
            Long worksheetId,
            GenerationClientMode mode,
            Map<AiArea, AiCorrectionLevel> settings) {}

    public record LegacyInput(Worksheet worksheet, List<WorksheetPage> pages, List<WorksheetSetting> settings) {}

    public record TranscriptionInput(StoredTranscription reused) {}

    @Transactional
    public Work claim(Long id) {
        WorksheetGeneration job = locked(id);
        if (job == null || job.getStatus() != GenerationStatus.PENDING || job.getClientMode() == null) return null;
        if (!current(job) || job.getWorksheet().getStatus() != WorksheetStatus.PROCESSING) {
            job.fail("STALE_JOB", "현재 학습지의 실행 대상이 아닌 작업입니다.", false);
            return null;
        }
        job.start();
        return new Work(
                id,
                job.getWorksheet().getAcademy().getId(),
                job.getWorksheet().getId(),
                job.getClientMode(),
                GenerationResultStorageService.settings(job));
    }

    @Transactional
    public StoredTranscription transcribed(Work work, PreparedWorksheetImage image, List<String> lines) {
        WorksheetGeneration job = running(work.id(), GenerationStage.OCR);
        if (job == null) return null;
        Long id = archives.recordTranscription(
                work.academyId(), work.id(), image, lines, GenerationClientProfiles.model(work.mode()));
        job.useTranscription(id);
        return archives.restoreTranscription(work.academyId(), id);
    }

    @Transactional
    public TranscriptionInput prepareTranscription(Work work, PreparedWorksheetImage image) {
        WorksheetGeneration job = running(work.id(), GenerationStage.OCR);
        if (job == null) return null;
        var saved = transcriptionReuse.resolve(job, work.mode(), job.getInputTranscriptionId(), image);
        if (saved != null) job.useTranscription(saved.id());
        return new TranscriptionInput(saved);
    }

    @Transactional
    public void completeFixed(Work work, StoredTranscription transcription, String raw, AiValidationResult validation) {
        WorksheetGeneration job = running(work.id(), GenerationStage.AI_CORRECTION);
        if (job == null) return;
        archives.recordValidatedResult(
                work.academyId(),
                work.id(),
                transcription.id(),
                GenerationClientProfiles.metadata(work.mode()),
                raw,
                validation);
        finish(job);
    }

    @Transactional
    public void unreadable(Work work, StoredTranscription transcription, String raw) {
        WorksheetGeneration job = running(work.id(), GenerationStage.AI_CORRECTION);
        if (job == null) return;
        archives.recordUnreadableResult(
                work.academyId(),
                work.id(),
                transcription.id(),
                GenerationClientProfiles.metadata(work.mode()),
                raw,
                "계약 v1 JSON 구조를 읽을 수 없습니다.");
        failJob(job, "INVALID_AI_RESPONSE", "계약 v1 JSON 구조를 읽을 수 없습니다.", true);
    }

    public LegacyInput legacyInput(Work work) {
        Worksheet worksheet = worksheets.findById(work.worksheetId()).orElseThrow();
        List<WorksheetSetting> selected = work.settings().entrySet().stream()
                .map(entry -> WorksheetSetting.builder()
                        .worksheet(worksheet)
                        .area(CorrectionArea.valueOf(entry.getKey().name()))
                        .level(CorrectionLevel.valueOf(entry.getValue().name()))
                        .build())
                .toList();
        return new LegacyInput(worksheet, pages.findAllByWorksheetIdOrderByPageNumberAsc(work.worksheetId()), selected);
    }

    @Transactional
    public void completeLegacy(Work work, List<CorrectionDraft> drafts) {
        WorksheetGeneration job = running(work.id(), GenerationStage.AI_CORRECTION);
        if (job == null) return;
        List<WorksheetPage> available = pages.findAllByWorksheetIdOrderByPageNumberAsc(work.worksheetId());
        for (var draft : drafts) {
            WorksheetPage page = available.stream()
                    .filter(p -> p.getId().equals(draft.pageId()))
                    .findFirst()
                    .orElseThrow();
            corrections.save(Correction.builder()
                    .worksheet(job.getWorksheet())
                    .page(page)
                    .generation(job)
                    .area(draft.area())
                    .originalText(draft.originalText())
                    .aiInitialContent(draft.aiInitialContent())
                    .content(draft.aiInitialContent())
                    .explanation(draft.explanation())
                    .regions(draft.regions())
                    .build());
        }
        finish(job);
    }

    @Transactional
    public void fail(Long id, String code, String message, boolean retryable) {
        WorksheetGeneration job = locked(id);
        if (job == null || job.getStatus() != GenerationStatus.RUNNING && job.getStatus() != GenerationStatus.PENDING)
            return;
        failJob(job, code, message, retryable);
    }

    public List<Long> pendingIds() {
        return generations.findBackgroundIds(GenerationStatus.PENDING, PageRequest.of(0, 32));
    }

    public List<Long> interruptedIds() {
        return generations.findInterruptedIds();
    }

    private WorksheetGeneration locked(Long id) {
        var worksheetId = generations.findWorksheetIdById(id).orElse(null);
        if (worksheetId == null) return null;
        // 접수와 동일하게 학습지 → 작업 순서로 잠근다.
        worksheets.findLockedById(worksheetId).orElseThrow();
        return generations.findLockedById(id).orElseThrow();
    }

    private WorksheetGeneration running(Long id, GenerationStage stage) {
        WorksheetGeneration job = locked(id);
        return job != null
                        && job.getStatus() == GenerationStatus.RUNNING
                        && job.getCurrentStage() == stage
                        && current(job)
                        && job.getWorksheet().getStatus() == WorksheetStatus.PROCESSING
                ? job
                : null;
    }

    private boolean current(WorksheetGeneration job) {
        return generations
                .findFirstByWorksheetIdOrderByCreatedAtDescIdDesc(
                        job.getWorksheet().getId())
                .orElseThrow()
                .getId()
                .equals(job.getId());
    }

    private void finish(WorksheetGeneration job) {
        corrections.findAllByWorksheetIdAndStatus(job.getWorksheet().getId(), CorrectionStatus.ACTIVE).stream()
                .filter(card -> !card.getGeneration().getId().equals(job.getId()))
                .forEach(Correction::delete);
        job.succeed();
        job.getWorksheet().updateStatus(WorksheetStatus.READY);
    }

    private void failJob(WorksheetGeneration job, String code, String message, boolean retryable) {
        job.fail(code, message, retryable);
        if (current(job) && job.getWorksheet().getStatus() == WorksheetStatus.PROCESSING)
            job.getWorksheet().updateStatus(WorksheetStatus.FAILED);
    }
}
