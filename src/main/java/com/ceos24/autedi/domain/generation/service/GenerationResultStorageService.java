package com.ceos24.autedi.domain.generation.service;

import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionAnchor;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponse;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponseParser;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionResponseValidator;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidatedItem;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationContext;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationResult;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.entity.WorksheetGenerationResult;
import com.ceos24.autedi.domain.generation.entity.WorksheetTranscription;
import com.ceos24.autedi.domain.generation.persistence.GenerationResultMetadata;
import com.ceos24.autedi.domain.generation.persistence.StoredCorrectionLink;
import com.ceos24.autedi.domain.generation.persistence.StoredGenerationResult;
import com.ceos24.autedi.domain.generation.persistence.StoredTranscription;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationResultRepository;
import com.ceos24.autedi.domain.generation.repository.WorksheetTranscriptionRepository;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.service.PreparedWorksheetImage;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.storage.LocalStorageService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 보존용 저장 진입점. HTTP·AI 호출·생성 상태 변경은 수행하지 않으며 기존 Mock 경로와 독립적이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenerationResultStorageService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AiCorrectionResponseParser PARSER = new AiCorrectionResponseParser();
    private static final AiCorrectionResponseValidator VALIDATOR = new AiCorrectionResponseValidator();
    private final WorksheetGenerationRepository generations;
    private final WorksheetPageRepository pages;
    private final WorksheetTranscriptionRepository transcriptions;
    private final WorksheetGenerationResultRepository results;
    private final CorrectionRepository corrections;
    private final WorksheetService images;

    @Transactional
    public Long recordTranscription(
            Long academyId, Long generationId, PreparedWorksheetImage image, List<String> lines, String model) {
        WorksheetGeneration generation = ownedGeneration(academyId, generationId);
        if (model == null || model.isBlank() || model.length() > 100)
            throw new IllegalArgumentException("전사 모델 정보가 필요합니다.");
        if (!generation.getWorksheet().getId().equals(image.worksheetId())
                || !generation
                        .getRequestFileHash()
                        .equals(generation.getWorksheet().getFileHash())) {
            throw new IllegalArgumentException("생성 요청의 학습지·원본 파일과 전사 입력이 일치하지 않습니다.");
        }
        WorksheetPage page = pages.findByIdAndWorksheetId(image.pageId(), image.worksheetId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAGE_NOT_FOUND));
        PreparedWorksheetImage current = images.getPreparedImage(academyId, image.worksheetId(), image.pageNumber());
        if (!current.pageId().equals(page.getId())
                || current.width() != image.width()
                || current.height() != image.height()
                || !current.sha256().equals(image.sha256())
                || !image.sha256().equals(LocalStorageService.sha256(image.png()))) {
            throw new IllegalArgumentException("전사 입력은 현재 보정 PNG와 같은 바이트·크기여야 합니다.");
        }
        List<String> checkedLines = new AiValidationContext(image.pageNumber(), lines, Map.of()).transcriptionLines();
        var existing = transcriptions.findByGenerationIdAndPageId(generationId, page.getId());
        if (existing.isPresent()) {
            WorksheetTranscription saved = existing.get();
            if (saved.getSourceImageHash().equals(image.sha256())
                    && saved.getModel().equals(model)
                    && saved.getLines().equals(checkedLines)) return saved.getId();
            throw new IllegalStateException("확정 전사를 덮어쓸 수 없습니다. 새 생성 작업으로 전사 버전을 추가하세요.");
        }
        return transcriptions
                .save(new WorksheetTranscription(generation, page, image.sha256(), model, checkedLines))
                .getId();
    }

    @Transactional
    public Long recordValidatedResult(
            Long academyId,
            Long generationId,
            Long transcriptionId,
            GenerationResultMetadata metadata,
            String rawResponse,
            AiValidationResult validation) {
        WorksheetGeneration generation = ownedGeneration(academyId, generationId);
        WorksheetTranscription transcription = ownedTranscription(academyId, transcriptionId);
        checkBinding(generation, transcription, metadata);
        AiCorrectionResponse parsed = PARSER.parse(rawResponse);
        AiValidationContext context =
                new AiValidationContext(transcription.getPageNumber(), transcription.getLines(), settings(generation));
        if (!VALIDATOR.validate(parsed, context).equals(validation)) {
            throw new IllegalArgumentException("원본 응답·확정 전사·요청 설정과 검증 결과가 일치하지 않습니다.");
        }
        var existing = results.findByGenerationIdAndPageId(
                generationId, transcription.getPage().getId());
        if (existing.isPresent())
            return sameResult(existing.get(), transcriptionId, metadata, rawResponse, validation, null);

        // 현재 화면과 다른 이미지에 활성 카드를 만들지 않는다. 과거 기록의 조회는 이 검사를 거치지 않는다.
        PreparedWorksheetImage current =
                images.getPreparedImage(academyId, generation.getWorksheet().getId(), transcription.getPageNumber());
        if (!current.sha256().equals(transcription.getSourceImageHash())) {
            throw new IllegalArgumentException("기준 PNG가 변경되어 과거 전사로 현재 첨삭 카드를 만들 수 없습니다.");
        }
        List<StoredCorrectionLink> links = new ArrayList<>();
        for (AiValidatedItem item : validation.corrections()) {
            Correction saved = corrections.save(toCorrection(generation, transcription, item));
            links.add(new StoredCorrectionLink(item.candidateIndex(), saved.getId()));
        }
        links.sort(Comparator.comparingInt(StoredCorrectionLink::candidateIndex));
        return results.save(new WorksheetGenerationResult(
                        generation, transcription, metadata, rawResponse, validation, links, null))
                .getId();
    }

    @Transactional
    public Long recordUnreadableResult(
            Long academyId,
            Long generationId,
            Long transcriptionId,
            GenerationResultMetadata metadata,
            String rawResponse,
            String parseError) {
        WorksheetGeneration generation = ownedGeneration(academyId, generationId);
        WorksheetTranscription transcription = ownedTranscription(academyId, transcriptionId);
        checkBinding(generation, transcription, metadata);
        Objects.requireNonNull(rawResponse, "rawResponse");
        if (parseError == null || parseError.isBlank()) throw new IllegalArgumentException("해석 실패 사유가 필요합니다.");
        boolean unreadable = false;
        try {
            PARSER.parse(rawResponse);
        } catch (IllegalArgumentException e) {
            unreadable = true;
        }
        if (!unreadable) throw new IllegalArgumentException("구조를 읽을 수 있는 응답은 검증 결과로 보존해야 합니다.");
        var existing = results.findByGenerationIdAndPageId(
                generationId, transcription.getPage().getId());
        if (existing.isPresent())
            return sameResult(existing.get(), transcriptionId, metadata, rawResponse, null, parseError);
        return results.save(new WorksheetGenerationResult(
                        generation, transcription, metadata, rawResponse, null, List.of(), parseError))
                .getId();
    }

    public StoredTranscription restoreTranscription(Long academyId, Long transcriptionId) {
        return ownedTranscription(academyId, transcriptionId).snapshot();
    }

    public StoredGenerationResult restoreResult(Long academyId, Long resultId) {
        return results.findByIdAndGenerationWorksheetAcademyId(resultId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.GENERATION_RESULT_NOT_FOUND))
                .snapshot();
    }

    private WorksheetGeneration ownedGeneration(Long academyId, Long generationId) {
        WorksheetGeneration generation = generations
                .findById(generationId)
                .orElseThrow(() -> new CustomException(ErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getWorksheet().getAcademy().getId().equals(academyId))
            throw new CustomException(ErrorCode.GENERATION_NOT_FOUND);
        return generation;
    }

    private WorksheetTranscription ownedTranscription(Long academyId, Long transcriptionId) {
        return transcriptions
                .findByIdAndGenerationWorksheetAcademyId(transcriptionId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSCRIPTION_NOT_FOUND));
    }

    private static void checkBinding(
            WorksheetGeneration generation, WorksheetTranscription transcription, GenerationResultMetadata metadata) {
        if (!generation
                        .getWorksheet()
                        .getId()
                        .equals(transcription.getPage().getWorksheet().getId())
                || !generation.getRequestFileHash().equals(transcription.getSourceFileHash())
                || !metadata.model().equals(transcription.getModel())
                || !metadata.contractVersion().equals(AiCorrectionResponseParser.CONTRACT_ID)
                || !metadata.validationVersion().equals(GenerationResultMetadata.VALIDATION_VERSION)) {
            throw new IllegalArgumentException("생성 작업·전사·파일·모델·계약 버전 연결이 올바르지 않습니다.");
        }
    }

    static Map<AiArea, AiCorrectionLevel> settings(WorksheetGeneration generation) {
        return settings(generation.getRequestSettingsSnapshot());
    }

    static Map<AiArea, AiCorrectionLevel> settings(Object value) {
        JsonNode snapshot = JSON.valueToTree(value);
        if (snapshot.isObject()) snapshot = snapshot.path("areas");
        if (!snapshot.isArray()) throw new IllegalArgumentException("생성 요청 설정 스냅샷에 areas 배열이 필요합니다.");
        Map<AiArea, AiCorrectionLevel> settings = new EnumMap<>(AiArea.class);
        for (JsonNode setting : snapshot) {
            if (!setting.path("area").isString() || !setting.path("level").isString())
                throw new IllegalArgumentException("영역·수준 스냅샷이 올바르지 않습니다.");
            AiArea area = AiArea.valueOf(
                    CorrectionArea.fromString(setting.get("area").asString()).name());
            AiCorrectionLevel previous = settings.put(
                    area, AiCorrectionLevel.valueOf(setting.get("level").asString()));
            if (previous != null) throw new IllegalArgumentException("중복 영역 스냅샷입니다.");
        }
        return Map.copyOf(settings);
    }

    private static Correction toCorrection(
            WorksheetGeneration generation, WorksheetTranscription transcription, AiValidatedItem item) {
        var original = item.original();
        var resolved = item.resolvedAnchor();
        String initial = original.action() == com.ceos24.autedi.domain.generation.contract.v1.AiAction.REPLACE
                ? original.replacement()
                : original.guidance();
        List<RegionPayload> regions = item.validBoxes().stream()
                .map(box -> new RegionPayload(
                        transcription.getPage().getId(),
                        box.get(0),
                        box.get(1),
                        box.get(2) - box.get(0),
                        box.get(3) - box.get(1),
                        null))
                .toList();
        return Correction.builder()
                .worksheet(generation.getWorksheet())
                .page(transcription.getPage())
                .generation(generation)
                .area(CorrectionArea.valueOf(original.area().name()))
                .originalText(original.anchor().original())
                .aiInitialContent(initial)
                .content(initial)
                .explanation(original.reason())
                .regions(regions)
                .action(original.action())
                .feedbackType(original.feedbackType())
                .issueKinds(original.issueKinds())
                .anchor(new CorrectionAnchor(
                        transcription.getId(),
                        resolved.lineId(),
                        original.anchor().occurrence(),
                        resolved.start(),
                        resolved.end()))
                .locationUncertain(original.locationUncertain())
                .warnings(item.issues())
                .requiresReview(item.requiresReview())
                .build();
    }

    private static Long sameResult(
            WorksheetGenerationResult existing,
            Long transcriptionId,
            GenerationResultMetadata metadata,
            String raw,
            AiValidationResult validation,
            String parseError) {
        StoredGenerationResult saved = existing.snapshot();
        if (saved.transcription().id().equals(transcriptionId)
                && saved.metadata().equals(metadata)
                && saved.rawResponse().equals(raw)
                && Objects.equals(saved.validationResult(), validation)
                && Objects.equals(saved.parseError(), parseError)) return saved.id();
        throw new IllegalStateException("최초 생성 결과를 덮어쓸 수 없습니다. 새 생성 작업으로 결과를 추가하세요.");
    }
}
