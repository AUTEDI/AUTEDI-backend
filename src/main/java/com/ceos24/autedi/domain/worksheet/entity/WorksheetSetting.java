package com.ceos24.autedi.domain.worksheet.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "worksheet_settings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_worksheet_settings_worksheet_id_area",
                        columnNames = {"worksheet_id", "area"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorksheetSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worksheet_id", nullable = false)
    private Worksheet worksheet;

    @Enumerated(EnumType.STRING)
    @Column(name = "area", length = 50, nullable = false)
    private CorrectionArea area;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 30, nullable = false)
    private CorrectionLevel level;

    @Builder
    public WorksheetSetting(Worksheet worksheet, CorrectionArea area, CorrectionLevel level) {
        this.worksheet = worksheet;
        this.area = area;
        this.level = level;
    }

    public void updateLevel(CorrectionLevel level) {
        this.level = level;
    }
}
