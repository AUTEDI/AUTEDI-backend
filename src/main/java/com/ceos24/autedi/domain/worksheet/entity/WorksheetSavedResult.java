package com.ceos24.autedi.domain.worksheet.entity;

import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 학습지별 마지막 명시적 저장본 한 개. 작업 중인 엔티티와 분리해 보존한다. */
@Entity
@Table(
        name = "worksheet_saved_results",
        indexes = {
            @Index(name = "idx_saved_results_saved_by", columnList = "saved_by"),
            @Index(name = "idx_saved_results_generation", columnList = "source_generation_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetSavedResult extends BaseTimeEntity {
    @Id
    @Column(name = "worksheet_id")
    private Long worksheetId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worksheet_id", foreignKey = @ForeignKey(name = "fk_saved_results_worksheet"))
    private Worksheet worksheet;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "source_file_hash", length = 64, nullable = false)
    private String sourceFileHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_generation_id", foreignKey = @ForeignKey(name = "fk_saved_results_generation"))
    private WorksheetGeneration sourceGeneration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saved_by", nullable = false, foreignKey = @ForeignKey(name = "fk_saved_results_user"))
    private User savedBy;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", columnDefinition = "json", nullable = false)
    private Map<String, Object> snapshot;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    public WorksheetSavedResult(Worksheet worksheet) {
        this.worksheet = worksheet;
    }

    public void replace(
            WorksheetGeneration generation, User savedBy, LocalDateTime savedAt, Map<String, Object> snapshot) {
        if (savedBy == null || savedAt == null || snapshot == null) throw new IllegalArgumentException("저장본이 필요합니다.");
        this.revision++;
        this.schemaVersion = 2;
        this.sourceFileHash = worksheet.getFileHash();
        this.sourceGeneration = generation;
        this.savedBy = savedBy;
        this.savedAt = savedAt;
        this.snapshot = snapshot;
    }

    public String getEtag() {
        return "\"saved_ws_" + worksheetId + "-r" + revision + "\"";
    }
}
