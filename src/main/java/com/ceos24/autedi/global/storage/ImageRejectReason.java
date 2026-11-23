package com.ceos24.autedi.global.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이미지 검사 거부 사유. 이름은 로그의 reason 값, message는 사용자 응답에 보이는 문구다. */
@Getter
@RequiredArgsConstructor
public enum ImageRejectReason {

    // WorksheetImagePreparer
    EMPTY_IMAGE("빈 이미지입니다."),
    FORMAT_MISMATCH("확장자와 실제 JPG/JPEG·PNG 형식이 일치해야 합니다."),
    NO_IMAGE_READER("이미지를 읽을 수 없습니다."),
    UNSUPPORTED_IMAGE_COUNT("한 장의 정지 이미지 또는 HDR 보조 이미지가 포함된 사진만 지원합니다."),
    DECODE_FAILED("이미지를 읽을 수 없습니다."),
    EXIF_HEADER_CORRUPT("손상된 EXIF 헤더입니다."),
    EXIF_BYTE_ORDER_UNSUPPORTED("지원하지 않는 EXIF 바이트 순서입니다."),
    TIFF_HEADER_CORRUPT("손상된 TIFF 헤더입니다."),
    EXIF_OFFSET_CORRUPT("손상된 EXIF 위치입니다."),
    EXIF_ENTRY_CORRUPT("손상된 EXIF 항목입니다."),
    EXIF_ORIENTATION_FORMAT_UNSUPPORTED("지원하지 않는 EXIF 방향 형식입니다."),
    EXIF_ORIENTATION_INVALID("잘못된 EXIF 방향입니다."),
    ORIENTATION_INVALID("잘못된 방향입니다."),

    // JpegGainMapMetadata
    HDR_AUX_LOCATION_NOT_FOUND("HDR 보조 이미지의 위치를 찾을 수 없습니다."),
    MPF_HEADER_CORRUPT("손상된 MPF 헤더입니다."),
    MPF_BYTE_ORDER_UNSUPPORTED("지원하지 않는 MPF 바이트 순서입니다."),
    MPF_OFFSET_CORRUPT("손상된 MPF 위치입니다."),
    HDR_AUX_INFO_INVALID("HDR 보조 이미지 정보가 올바르지 않습니다."),
    HDR_AUX_OFFSET_INVALID("HDR 보조 이미지 위치가 올바르지 않습니다."),
    HDR_AUX_INFO_NOT_FOUND("HDR 보조 이미지 정보를 찾을 수 없습니다."),
    HDR_AUX_NOT_JPEG("HDR 보조 이미지가 JPEG가 아닙니다."),
    HDR_XMP_UNREADABLE("HDR 보조 이미지의 XMP 정보를 읽을 수 없습니다."),
    HDR_XMP_MISMATCH("HDR 보조 이미지의 XMP 정보가 서로 다릅니다.");

    private final String message;
}
