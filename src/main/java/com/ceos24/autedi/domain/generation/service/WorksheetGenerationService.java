package com.ceos24.autedi.domain.generation.service;

import com.ceos24.autedi.domain.generation.dto.response.GenerationResponse;
import com.ceos24.autedi.domain.generation.entity.GenerationClientMode;
import com.ceos24.autedi.domain.generation.entity.GenerationStatus;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSettingRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 접수 데이터만 저장한다. 이벤트는 커밋 후 워커에 전달된다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorksheetGenerationService {
    private final WorksheetRepository worksheets;
    private final WorksheetSettingRepository settings;
    private final WorksheetGenerationRepository generations;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final GenerationTranscriptionReuse transcriptionReuse;

    @Value("${app.ai.fixed-response.enabled:false}")
    private boolean fixedResponseEnabled;

    @Value("${app.ai.client:#{null}}")
    private GenerationClientMode configuredClient;

    @Transactional
    public GenerationResponse requestGeneration(Long userId, Long academyId, Long worksheetId, String key) {
        Worksheet worksheet = ownedForUpdate(academyId, worksheetId);
        key = key(key);
        User user = activeUser(userId, academyId);
        WorksheetGeneration existing = existing(key, worksheetId, null);
        if (existing != null) return GenerationResponse.from(existing);
        if (worksheet.getStatus() == WorksheetStatus.PROCESSING)
            throw new CustomException(ErrorCode.GENERATION_IN_PROGRESS);
        if (worksheet.getStatus() == WorksheetStatus.READY) throw new CustomException(ErrorCode.ALREADY_GENERATED);
        return accept(worksheet, user, key, snapshot(worksheetId), null);
    }

    public GenerationResponse getGenerationStatus(Long academyId, Long worksheetId, Long generationId) {
        worksheets
                .findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));
        return GenerationResponse.from(target(worksheetId, generationId));
    }

    @Transactional
    public GenerationResponse retryGeneration(
            Long userId, Long academyId, Long worksheetId, Long generationId, String key) {
        Worksheet worksheet = ownedForUpdate(academyId, worksheetId);
        key = key(key);
        WorksheetGeneration target = target(worksheetId, generationId);
        User user = activeUser(userId, academyId);
        WorksheetGeneration existing = existing(key, worksheetId, generationId);
        if (existing != null) return GenerationResponse.from(existing);
        List<Map<String, String>> current = snapshot(worksheetId);
        Long latest = generations
                .findFirstByWorksheetIdOrderByCreatedAtDescIdDesc(worksheetId)
                .orElseThrow()
                .getId();
        if (!latest.equals(generationId)
                || target.getStatus() != GenerationStatus.FAILED
                || !Boolean.TRUE.equals(target.getIsRetryable())
                || worksheet.getStatus() != WorksheetStatus.FAILED
                || !worksheet.getFileHash().equals(target.getRequestFileHash())
                || !sameSettings(current, target)) {
            throw new CustomException(ErrorCode.CANNOT_RETRY_GENERATION);
        }
        return accept(worksheet, user, key, current, target);
    }

    private GenerationResponse accept(
            Worksheet worksheet,
            User user,
            String key,
            List<Map<String, String>> snapshot,
            WorksheetGeneration previous) {
        GenerationClientMode mode = previous != null && previous.getClientMode() != null
                ? previous.getClientMode()
                : configuredClient != null
                        ? configuredClient
                        : fixedResponseEnabled ? GenerationClientMode.FIXED : GenerationClientMode.LEGACY_MOCK;
        Long transcriptionId = previous == null ? null : transcriptionReuse.findForRetry(previous, mode);
        WorksheetGeneration generation = generations.save(WorksheetGeneration.builder()
                .worksheet(worksheet)
                .requestedBy(user)
                .idempotencyKey(key)
                .requestFileHash(worksheet.getFileHash())
                .requestSettingsSnapshot(snapshot)
                .clientMode(mode)
                .inputTranscriptionId(transcriptionId)
                .retryCount(previous == null ? 0 : previous.getRetryCount() + 1)
                .retryOfGenerationId(previous == null ? null : previous.getId())
                .build());
        worksheet.updateStatus(WorksheetStatus.PROCESSING);
        events.publishEvent(new GenerationRequested(generation.getId()));
        return GenerationResponse.from(generation);
    }

    private List<Map<String, String>> snapshot(Long worksheetId) {
        var selected = settings.findAllByWorksheetId(worksheetId);
        if (selected.isEmpty()) throw new CustomException(ErrorCode.NO_SETTINGS_SELECTED);
        return selected.stream()
                .map(s ->
                        Map.of("area", s.getArea().name(), "level", s.getLevel().name()))
                .toList();
    }

    private static boolean sameSettings(List<Map<String, String>> current, WorksheetGeneration previous) {
        try {
            return GenerationResultStorageService.settings(current)
                    .equals(GenerationResultStorageService.settings(previous));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Worksheet ownedForUpdate(Long academyId, Long worksheetId) {
        return worksheets
                .findLockedByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));
    }

    private WorksheetGeneration target(Long worksheetId, Long id) {
        var generation =
                generations.findById(id).orElseThrow(() -> new CustomException(ErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getWorksheet().getId().equals(worksheetId))
            throw new CustomException(ErrorCode.GENERATION_NOT_FOUND);
        return generation;
    }

    private WorksheetGeneration existing(String key, Long worksheetId, Long retryTarget) {
        var existing = generations.findByIdempotencyKey(key).orElse(null);
        if (existing != null
                && (!existing.getWorksheet().getId().equals(worksheetId)
                        || !Objects.equals(existing.getRetryOfGenerationId(), retryTarget)))
            throw new CustomException(ErrorCode.GENERATION_REQUEST_CONFLICT);
        return existing;
    }

    private User activeUser(Long id, Long academyId) {
        User user = users.findById(id).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.getAcademy().getId().equals(academyId) || !user.isActive())
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        if (!user.getAcademy().isActive()) throw new CustomException(ErrorCode.ACADEMY_SUBSCRIPTION_INACTIVE);
        return user;
    }

    private static String key(String key) {
        if (key != null && key.length() > 100)
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "멱등키는 100자 이하여야 합니다.");
        return StringUtils.hasText(key) ? key : UUID.randomUUID().toString();
    }
}
