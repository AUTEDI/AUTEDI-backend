package com.ceos24.autedi.domain.worksheet.entity;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.user.entity.User;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "worksheets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Worksheet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id", nullable = false)
    private Academy academy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private WorksheetStatus status;

    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    @Column(name = "file_url", length = 500, nullable = false)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", length = 10, nullable = false)
    private FileType fileType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_hash", length = 64, nullable = false)
    private String fileHash;

    @Column(name = "page_count", nullable = false)
    private Integer pageCount;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Builder
    public Worksheet(
            Academy academy,
            User user,
            String originalFilename,
            String fileUrl,
            FileType fileType,
            Long fileSize,
            String fileHash,
            Integer pageCount
    ) {
        this.academy = academy;
        this.user = user;
        this.status = WorksheetStatus.DRAFT;
        this.originalFilename = originalFilename;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.pageCount = pageCount != null ? pageCount : 1;
        this.version = 1;
    }

    public boolean canModifyOrReplace() {
        return this.status == WorksheetStatus.DRAFT || this.status == WorksheetStatus.FAILED;
    }

    public void replaceFile(
            String originalFilename,
            String fileUrl,
            FileType fileType,
            Long fileSize,
            String fileHash
    ) {
        this.originalFilename = originalFilename;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.status = WorksheetStatus.DRAFT;
    }

    public void updateStatus(WorksheetStatus status) {
        this.status = status;
    }

    public String getEtag() {
        return "\"ws_" + this.id + "-v" + (this.version != null ? this.version : 1) + "\"";
    }
}
