package com.ceos24.autedi.domain.generation.service;

import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.generation.client.CorrectionAiClient;
import com.ceos24.autedi.domain.generation.client.CorrectionDraft;
import com.ceos24.autedi.domain.generation.dto.response.GenerationResponse;
import com.ceos24.autedi.domain.generation.entity.GenerationStage;
import com.ceos24.autedi.domain.generation.entity.GenerationStatus;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorksheetGenerationService {

    private final WorksheetRepository worksheetRepository;
    private final WorksheetPageRepository worksheetPageRepository;
    private final WorksheetSettingRepository worksheetSettingRepository;
    private final WorksheetGenerationRepository worksheetGenerationRepository;
    private final CorrectionRepository correctionRepository;
    private final UserRepository userRepository;
    private final CorrectionAiClient correctionAiClient;

    @Transactional
    public GenerationResponse requestGeneration(
            Long userId,
            Long academyId,
            Long worksheetId,
            String idempotencyKey
    ) {
        // 멱등성 검증: 동일한 키로 이미 요청된 작업이 있다면 기존 작업 응답 반환
        if (StringUtils.hasText(idempotencyKey)) {
            Optional<WorksheetGeneration> existing = worksheetGenerationRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent() && existing.get().getWorksheet().getId().equals(worksheetId)) {
                log.info("멱등성 캐시 히트: idempotencyKey={}", idempotencyKey);
                return GenerationResponse.from(existing.get());
            }
        } else {
            idempotencyKey = UUID.randomUUID().toString();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!user.getAcademy().isActive()) {
            throw new CustomException(ErrorCode.ACADEMY_SUBSCRIPTION_INACTIVE);
        }

        Worksheet worksheet = worksheetRepository.findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));

        // 상태 제약 검증
        if (worksheet.getStatus() == WorksheetStatus.PROCESSING) {
            throw new CustomException(ErrorCode.GENERATION_IN_PROGRESS);
        }
        if (worksheet.getStatus() == WorksheetStatus.READY) {
            throw new CustomException(ErrorCode.ALREADY_GENERATED);
        }

        // 설정 존재 여부 검증
        List<WorksheetSetting> settings = worksheetSettingRepository.findAllByWorksheetId(worksheetId);
        if (settings.isEmpty()) {
            throw new CustomException(ErrorCode.NO_SETTINGS_SELECTED);
        }

        // 설정 스냅샷 생성
        List<Map<String, String>> settingsSnapshot = settings.stream()
                .map(s -> Map.of("area", s.getArea().name(), "level", s.getLevel().name()))
                .toList();

        // 생성 작업 엔티티 생성
        WorksheetGeneration generation = WorksheetGeneration.builder()
                .worksheet(worksheet)
                .requestedBy(user)
                .idempotencyKey(idempotencyKey)
                .requestFileHash(worksheet.getFileHash())
                .requestSettingsSnapshot(settingsSnapshot)
                .build();
        worksheetGenerationRepository.save(generation);

        // 학습지 상태 변경: PROCESSING
        worksheet.updateStatus(WorksheetStatus.PROCESSING);

        // 비동기/Mock 작업 수행 (단일 트랜잭션 내에서 완료 처리하여 E2E 테스트 지원)
        executeAiCorrection(generation, worksheet, settings);

        return GenerationResponse.from(generation);
    }

    public GenerationResponse getGenerationStatus(Long academyId, Long worksheetId, Long generationId) {
        worksheetRepository.findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));

        WorksheetGeneration generation = worksheetGenerationRepository.findById(generationId)
                .orElseThrow(() -> new CustomException(ErrorCode.GENERATION_NOT_FOUND));

        if (!generation.getWorksheet().getId().equals(worksheetId)) {
            throw new CustomException(ErrorCode.GENERATION_NOT_FOUND);
        }

        return GenerationResponse.from(generation);
    }

    @Transactional
    public GenerationResponse retryGeneration(
            Long userId,
            Long academyId,
            Long worksheetId,
            Long generationId,
            String idempotencyKey
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            idempotencyKey = UUID.randomUUID().toString();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Worksheet worksheet = worksheetRepository.findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));

        WorksheetGeneration target = worksheetGenerationRepository.findById(generationId)
                .orElseThrow(() -> new CustomException(ErrorCode.GENERATION_NOT_FOUND));

        // 재시도 조건 검증: 실패 작업이어야 함
        if (target.getStatus() != GenerationStatus.FAILED) {
            throw new CustomException(ErrorCode.CANNOT_RETRY_GENERATION, "실패한 작업만 재시도할 수 있습니다.");
        }

        // 파일 변경 여부 검증
        if (!worksheet.getFileHash().equals(target.getRequestFileHash())) {
            throw new CustomException(ErrorCode.CANNOT_RETRY_GENERATION, "파일이 변경되어 재시도할 수 없습니다. 새 생성을 요청해 주세요.");
        }

        List<WorksheetSetting> settings = worksheetSettingRepository.findAllByWorksheetId(worksheetId);
        List<Map<String, String>> currentSnapshot = settings.stream()
                .map(s -> Map.of("area", s.getArea().name(), "level", s.getLevel().name()))
                .toList();

        // 새 작업 생성
        WorksheetGeneration newGeneration = WorksheetGeneration.builder()
                .worksheet(worksheet)
                .requestedBy(user)
                .idempotencyKey(idempotencyKey)
                .requestFileHash(worksheet.getFileHash())
                .requestSettingsSnapshot(currentSnapshot)
                .retryCount(target.getRetryCount() + 1)
                .retryOfGenerationId(target.getId())
                .build();
        worksheetGenerationRepository.save(newGeneration);

        worksheet.updateStatus(WorksheetStatus.PROCESSING);

        executeAiCorrection(newGeneration, worksheet, settings);

        return GenerationResponse.from(newGeneration);
    }

    private void executeAiCorrection(
            WorksheetGeneration generation,
            Worksheet worksheet,
            List<WorksheetSetting> settings
    ) {
        generation.start();
        List<WorksheetPage> pages = worksheetPageRepository.findAllByWorksheetIdOrderByPageNumberAsc(worksheet.getId());

        generation.updateStage(GenerationStage.AI_CORRECTION);

        // AI 클라이언트를 통한 첨삭 항목 초안 생성
        List<CorrectionDraft> drafts = correctionAiClient.generate(worksheet, pages, settings);

        // 이전 활성 첨삭 항목이 있다면 정리
        List<Correction> oldCorrections = correctionRepository.findAllByWorksheetIdAndStatus(
                worksheet.getId(),
                com.ceos24.autedi.domain.correction.entity.CorrectionStatus.ACTIVE
        );
        oldCorrections.forEach(Correction::delete);

        // 생성된 첨삭 항목 영속화
        List<Correction> newCorrections = drafts.stream().map(d -> {
            WorksheetPage matchedPage = pages.stream()
                    .filter(p -> p.getId().equals(d.pageId()))
                    .findFirst()
                    .orElse(pages.get(0));

            return Correction.builder()
                    .worksheet(worksheet)
                    .page(matchedPage)
                    .generation(generation)
                    .area(d.area())
                    .originalText(d.originalText())
                    .aiInitialContent(d.aiInitialContent())
                    .content(d.aiInitialContent())
                    .explanation(d.explanation())
                    .regions(d.regions())
                    .build();
        }).toList();
        correctionRepository.saveAll(newCorrections);

        // 완료 처리 및 학습지 상태 READY(검토 준비 완료) 변경
        generation.succeed();
        worksheet.updateStatus(WorksheetStatus.READY);

        log.info("AI 첨삭 생성 완료: worksheetId={}, generationId={}, correctionsCount={}",
                worksheet.getId(), generation.getId(), newCorrections.size());
    }
}
