package com.ceos24.autedi.domain.generation.entity;

import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "worksheet_generations",
        uniqueConstraints = @UniqueConstraint(name = "UK_generation_idempotency_key", columnNames = "idempotency_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetGeneration extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worksheet_id", nullable = false)
    private Worksheet worksheet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "idempotency_key", length = 100, nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private GenerationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", length = 30, nullable = false)
    private GenerationStage currentStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_mode", length = 30, updatable = false)
    private GenerationClientMode clientMode;

    @Column(name = "request_file_hash", length = 64, nullable = false)
    private String requestFileHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_settings_snapshot", columnDefinition = "json", nullable = false)
    private Object requestSettingsSnapshot;

    @Column(name = "is_retryable", nullable = false)
    private Boolean isRetryable;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "retry_of_generation_id")
    private Long retryOfGenerationId;

    // JSON anchor와 동일한 논리 참조. 저장·재사용 시 학원/학습지/파일/PNG 연결을 확인한다.
    @Column(name = "input_transcription_id")
    private Long inputTranscriptionId;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    public WorksheetGeneration(
            Worksheet worksheet,
            User requestedBy,
            String idempotencyKey,
            String requestFileHash,
            Object requestSettingsSnapshot,
            Integer retryCount,
            Long retryOfGenerationId,
            GenerationClientMode clientMode,
            Long inputTranscriptionId) {
        this.worksheet = worksheet;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.status = GenerationStatus.PENDING;
        this.currentStage = GenerationStage.QUEUED;
        this.clientMode = clientMode;
        this.inputTranscriptionId = inputTranscriptionId;
        this.requestFileHash = requestFileHash;
        this.requestSettingsSnapshot = requestSettingsSnapshot;
        this.isRetryable = false;
        this.retryCount = retryCount != null ? retryCount : 0;
        this.retryOfGenerationId = retryOfGenerationId;
        this.version = 1;
    }

    public void start() {
        this.status = GenerationStatus.RUNNING;
        this.currentStage = GenerationStage.OCR;
        this.startedAt = LocalDateTime.now();
    }

    public void updateStage(GenerationStage stage) {
        this.currentStage = stage;
    }

    public void useTranscription(Long id) {
        if (status != GenerationStatus.RUNNING || currentStage != GenerationStage.OCR || id == null)
            throw new IllegalStateException("OCR 단계에서만 전사를 확정할 수 있습니다.");
        this.inputTranscriptionId = id;
        this.currentStage = GenerationStage.AI_CORRECTION;
    }

    public void succeed() {
        this.status = GenerationStatus.SUCCEEDED;
        this.currentStage = GenerationStage.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorCode, String errorMessage, boolean isRetryable) {
        this.status = GenerationStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.isRetryable = isRetryable;
        this.completedAt = LocalDateTime.now();
    }

    public Integer getAttempt() {
        return this.retryCount != null ? this.retryCount + 1 : 1;
    }
}
