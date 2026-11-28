package com.ceos24.autedi.domain.insight.entity;

import com.ceos24.autedi.domain.insight.dto.StudentInsightResponse.Status;
import com.ceos24.autedi.domain.insight.repository.InsightSource;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 학생별 마지막 성공 결과 하나. 실패한 호출은 이 행을 덮어쓰지 않는다. */
@Entity
@Table(
        name = "student_insights",
        indexes = @Index(name = "idx_student_insights_generated_by", columnList = "generated_by"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentInsight extends BaseTimeEntity {
    @Id
    @Column(name = "student_id")
    private Long studentId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", foreignKey = @ForeignKey(name = "fk_student_insights_student"))
    private Student student;

    @Column(name = "input_hash", length = 64, nullable = false)
    private String inputHash;

    @Column(name = "model", length = 100, nullable = false)
    private String model;

    @Column(name = "prompt_version", length = 60, nullable = false)
    private String promptVersion;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private Status status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_results", columnDefinition = "json", nullable = false)
    private List<InsightSource> sourceResults;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "json", nullable = false)
    private Map<String, Object> result;

    @Column(name = "used_result_count", nullable = false)
    private int usedResultCount;

    @Column(name = "analysis_from", nullable = false)
    private LocalDate analysisFrom;

    @Column(name = "analysis_to", nullable = false)
    private LocalDate analysisTo;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generated_by", nullable = false, foreignKey = @ForeignKey(name = "fk_student_insights_user"))
    private User generatedBy;

    @Version
    @Column(nullable = false)
    private Integer version;

    public StudentInsight(Student student) {
        this.student = student;
    }

    public void replace(
            String hash,
            String model,
            String promptVersion,
            Status status,
            List<InsightSource> sources,
            Map<String, Object> result,
            int used,
            LocalDate from,
            LocalDate to,
            LocalDateTime now,
            User user) {
        if (status != Status.READY && status != Status.NO_FEEDBACK) throw new IllegalArgumentException("성공 결과만 저장합니다.");
        this.inputHash = hash;
        this.model = model;
        this.promptVersion = promptVersion;
        this.schemaVersion = 1;
        this.status = status;
        this.sourceResults = List.copyOf(sources);
        this.result = Map.copyOf(result);
        this.usedResultCount = used;
        this.analysisFrom = from;
        this.analysisTo = to;
        this.generatedAt = now;
        this.generatedBy = user;
    }
}
