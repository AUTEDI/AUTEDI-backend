package com.ceos24.autedi.domain.generation.entity;

import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationContext;
import com.ceos24.autedi.domain.generation.persistence.StoredTranscription;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.global.entity.BaseCreatedTimeEntity;
import com.ceos24.autedi.global.storage.LocalStorageService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** 페이지의 가변 OCR 캐시와 별도로 보존하는 확정 전사문. 갱신 메서드를 제공하지 않는다. */
@Entity
@Immutable
@Table(
        name = "worksheet_transcriptions",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "UK_transcription_generation_page",
                        columnNames = {"generation_id", "page_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetTranscription extends BaseCreatedTimeEntity {
    public static final String NORMALIZATION_VERSION = "LF-PRESERVE-v1";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false, updatable = false)
    private WorksheetGeneration generation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false, updatable = false)
    private WorksheetPage page;

    @Column(name = "page_number", nullable = false, updatable = false)
    private int pageNumber;

    @Column(name = "source_file_hash", length = 64, nullable = false, updatable = false)
    private String sourceFileHash;

    @Column(name = "source_image_url", length = 500, nullable = false, updatable = false)
    private String sourceImageUrl;

    @Column(name = "source_image_hash", length = 64, nullable = false, updatable = false)
    private String sourceImageHash;

    @Column(name = "image_width", nullable = false, updatable = false)
    private int imageWidth;

    @Column(name = "image_height", nullable = false, updatable = false)
    private int imageHeight;

    @Column(name = "model", length = 100, nullable = false, updatable = false)
    private String model;

    @Column(name = "normalization_version", length = 100, nullable = false, updatable = false)
    private String normalizationVersion;

    @Column(name = "transcription_text", columnDefinition = "LONGTEXT", nullable = false, updatable = false)
    private String text;

    @Column(name = "text_hash", length = 64, nullable = false, updatable = false)
    private String textHash;

    public WorksheetTranscription(
            WorksheetGeneration generation,
            WorksheetPage page,
            String sourceImageHash,
            String model,
            List<String> lines) {
        AiValidationContext context = new AiValidationContext(page.getPageNumber(), lines, Map.of());
        this.generation = generation;
        this.page = page;
        this.pageNumber = page.getPageNumber();
        this.sourceFileHash = generation.getRequestFileHash();
        // 공개 API URL은 교체 후 다른 파일을 가리킬 수 있으므로 UUID 저장 URL을 고정한다.
        this.sourceImageUrl = page.getPreviewImageUrl();
        this.sourceImageHash = sourceImageHash;
        this.imageWidth = page.getWidth();
        this.imageHeight = page.getHeight();
        this.model = model;
        this.normalizationVersion = NORMALIZATION_VERSION;
        this.text = String.join("\n", context.transcriptionLines());
        this.textHash = LocalStorageService.sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    public List<String> getLines() {
        return List.of(text.split("\n", -1));
    }

    public StoredTranscription snapshot() {
        return new StoredTranscription(
                id,
                generation.getId(),
                generation.getWorksheet().getId(),
                page.getId(),
                pageNumber,
                sourceFileHash,
                sourceImageUrl,
                sourceImageHash,
                imageWidth,
                imageHeight,
                model,
                normalizationVersion,
                textHash,
                getLines());
    }
}
