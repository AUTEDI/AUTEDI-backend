package com.ceos24.autedi.domain.correction.entity;

import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "corrections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Correction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worksheet_id", nullable = false)
    private Worksheet worksheet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private WorksheetPage page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false)
    private WorksheetGeneration generation;

    @Enumerated(EnumType.STRING)
    @Column(name = "area", length = 50, nullable = false)
    private CorrectionArea area;

    @Column(name = "original_text", columnDefinition = "TEXT", nullable = false)
    private String originalText;

    @Column(name = "ai_initial_content", columnDefinition = "TEXT", nullable = false)
    private String aiInitialContent;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "explanation", columnDefinition = "TEXT", nullable = false)
    private String explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regions", columnDefinition = "json", nullable = false)
    private List<RegionPayload> regions;

    @Column(name = "is_edited", nullable = false)
    private Boolean isEdited;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CorrectionStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Correction(
            Worksheet worksheet,
            WorksheetPage page,
            WorksheetGeneration generation,
            CorrectionArea area,
            String originalText,
            String aiInitialContent,
            String content,
            String explanation,
            List<RegionPayload> regions
    ) {
        this.worksheet = worksheet;
        this.page = page;
        this.generation = generation;
        this.area = area;
        this.originalText = originalText;
        this.aiInitialContent = aiInitialContent;
        this.content = content != null ? content : aiInitialContent;
        this.explanation = explanation;
        this.regions = regions;
        this.isEdited = false;
        this.status = CorrectionStatus.ACTIVE;
        this.version = 1;
    }

    public void editContent(String newContent) {
        this.content = newContent;
        this.isEdited = true;
    }

    public void delete() {
        this.status = CorrectionStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.status = CorrectionStatus.ACTIVE;
        this.deletedAt = null;
    }

    public String getEtag() {
        return "\"cor_" + id + "-v" + (version != null ? version : 1) + "\"";
    }
}
