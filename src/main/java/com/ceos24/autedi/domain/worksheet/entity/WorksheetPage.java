package com.ceos24.autedi.domain.worksheet.entity;

import com.ceos24.autedi.global.entity.BaseCreatedTimeEntity;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "worksheet_pages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_worksheet_pages_worksheet_id_page_number",
                        columnNames = {"worksheet_id", "page_number"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetPage extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worksheet_id", nullable = false)
    private Worksheet worksheet;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "preview_image_url", length = 500, nullable = false)
    private String previewImageUrl;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "height", nullable = false)
    private Integer height;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocr_result", columnDefinition = "json")
    private Object ocrResult;

    @Column(name = "ocr_file_hash", length = 64)
    private String ocrFileHash;

    @Builder
    public WorksheetPage(
            Worksheet worksheet,
            Integer pageNumber,
            String previewImageUrl,
            Integer width,
            Integer height,
            Object ocrResult,
            String ocrFileHash
    ) {
        this.worksheet = worksheet;
        this.pageNumber = pageNumber;
        this.previewImageUrl = previewImageUrl;
        this.width = width;
        this.height = height;
        this.ocrResult = ocrResult;
        this.ocrFileHash = ocrFileHash;
    }

    public void updatePreview(String previewImageUrl, Integer width, Integer height) {
        this.previewImageUrl = previewImageUrl;
        this.width = width;
        this.height = height;
    }

    public void updateOcrResult(Object ocrResult, String ocrFileHash) {
        this.ocrResult = ocrResult;
        this.ocrFileHash = ocrFileHash;
    }
}
