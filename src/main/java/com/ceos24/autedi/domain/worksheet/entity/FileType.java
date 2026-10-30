package com.ceos24.autedi.domain.worksheet.entity;

public enum FileType {
    JPG,
    JPEG,
    PNG,
    PDF;

    public static FileType fromExtension(String extension) {
        if (extension == null) {
            throw new IllegalArgumentException("파일 확장자가 누락되었습니다.");
        }
        String upper = extension.toUpperCase().replace(".", "");
        return FileType.valueOf(upper);
    }
}
