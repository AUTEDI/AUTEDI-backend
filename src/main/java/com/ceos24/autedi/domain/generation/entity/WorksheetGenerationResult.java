package com.ceos24.autedi.domain.generation.entity;

import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationResult;
import com.ceos24.autedi.domain.generation.persistence.GenerationResultMetadata;
import com.ceos24.autedi.domain.generation.persistence.StoredCorrectionLink;
import com.ceos24.autedi.domain.generation.persistence.StoredGenerationResult;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.CheckConstraint;
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
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 최초 원문과 서버 검증 결과의 불변 아카이브. 확인·제외 후보도 validationResult에 전부 남긴다. */
@Entity
@Immutable
@Table(
        name = "worksheet_generation_results",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "UK_generation_result_page",
                        columnNames = {"generation_id", "page_id"}),
        check =
                @CheckConstraint(
                        name = "CHK_generation_result_status",
                        constraint =
                                "(status = 'VALIDATED' AND validation_result IS NOT NULL AND parse_error IS NULL) OR "
                                        + "(status = 'UNREADABLE' AND validation_result IS NULL AND parse_error IS NOT NULL)"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetGenerationResult extends BaseCreatedTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false, updatable = false)
    private WorksheetGeneration generation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false, updatable = false)
    private WorksheetPage page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transcription_id", nullable = false, updatable = false)
    private WorksheetTranscription transcription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false, updatable = false)
    private GenerationResultStatus status;

    @Column(name = "model", length = 100, nullable = false, updatable = false)
    private String model;

    @Column(name = "contract_version", length = 100, nullable = false, updatable = false)
    private String contractVersion;

    @Column(name = "prompt_version", length = 100, nullable = false, updatable = false)
    private String promptVersion;

    @Column(name = "validation_version", length = 100, nullable = false, updatable = false)
    private String validationVersion;

    @Column(name = "raw_response", columnDefinition = "LONGTEXT", nullable = false, updatable = false)
    private String rawResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result", columnDefinition = "json", updatable = false)
    private AiValidationResult validationResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correction_links", columnDefinition = "json", nullable = false, updatable = false)
    private List<StoredCorrectionLink> correctionLinks;

    @Column(name = "parse_error", columnDefinition = "TEXT", updatable = false)
    private String parseError;

    public WorksheetGenerationResult(
            WorksheetGeneration generation,
            WorksheetTranscription transcription,
            GenerationResultMetadata metadata,
            String rawResponse,
            AiValidationResult validationResult,
            List<StoredCorrectionLink> links,
            String parseError) {
        if (rawResponse == null
                || (validationResult == null) == (parseError == null)
                || parseError != null && parseError.isBlank()) {
            throw new IllegalArgumentException("원본 응답과 검증 결과 또는 파싱 실패 사유가 필요합니다.");
        }
        if (validationResult == null && !links.isEmpty())
            throw new IllegalArgumentException("해석 실패에는 정상 카드를 연결할 수 없습니다.");
        this.generation = generation;
        this.page = transcription.getPage();
        this.transcription = transcription;
        this.status = validationResult == null ? GenerationResultStatus.UNREADABLE : GenerationResultStatus.VALIDATED;
        this.model = metadata.model();
        this.contractVersion = metadata.contractVersion();
        this.promptVersion = metadata.promptVersion();
        this.validationVersion = metadata.validationVersion();
        this.rawResponse = rawResponse;
        this.validationResult = validationResult;
        this.correctionLinks = List.copyOf(links);
        this.parseError = parseError;
    }

    public StoredGenerationResult snapshot() {
        return new StoredGenerationResult(
                id,
                generation.getId(),
                transcription.snapshot(),
                status,
                new GenerationResultMetadata(model, contractVersion, promptVersion, validationVersion),
                rawResponse,
                validationResult,
                correctionLinks,
                parseError);
    }
}
