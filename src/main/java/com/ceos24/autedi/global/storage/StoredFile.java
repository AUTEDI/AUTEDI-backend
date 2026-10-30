package com.ceos24.autedi.global.storage;

import com.ceos24.autedi.domain.worksheet.entity.FileType;

public record StoredFile(
        String originalFilename,
        String storedFilename,
        String fileUrl,
        FileType fileType,
        Long fileSize,
        String fileHash,
        Integer width,
        Integer height) {}
