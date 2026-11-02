package com.ceos24.autedi.global.storage;

import com.ceos24.autedi.domain.worksheet.entity.FileType;

public record StoredFile(
        String originalFilename,
        String storedFilename,
        String fileUrl,
        FileType fileType,
        Long fileSize,
        String fileHash,
        String preparedImageUrl,
        Integer width,
        Integer height) {
    /** 보정 PNG가 없던 이전 저장 구현의 반환값 호환용. AI 입력에서는 별도로 준비 여부를 검사한다. */
    public StoredFile(
            String originalFilename,
            String storedFilename,
            String fileUrl,
            FileType fileType,
            Long fileSize,
            String fileHash,
            Integer width,
            Integer height) {
        this(originalFilename, storedFilename, fileUrl, fileType, fileSize, fileHash, fileUrl, width, height);
    }
}
