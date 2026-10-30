package com.ceos24.autedi.domain.comment.entity;

import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.user.entity.User;
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
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

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
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "highlight_type", length = 20, nullable = false)
    private HighlightType highlightType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regions", columnDefinition = "json", nullable = false)
    private List<RegionPayload> regions;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Comment(
            Worksheet worksheet,
            WorksheetPage page,
            User user,
            HighlightType highlightType,
            List<RegionPayload> regions,
            String content) {
        this.worksheet = worksheet;
        this.page = page;
        this.user = user;
        this.highlightType = highlightType;
        this.regions = regions;
        this.content = content;
        this.version = 1;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public String getEtag() {
        return "\"com_" + id + "-v" + (version != null ? version : 1) + "\"";
    }
}
