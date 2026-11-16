package com.ceos24.autedi.domain.worksheet.dto.response;

/** 파일시스템 URL 대신 학원 권한을 검증하는 조회 URL을 외부에 제공한다. */
public final class WorksheetFileUrls {
    private WorksheetFileUrls() {}

    public static String original(String storageUrl, Long worksheetId) {
        return endpoint(storageUrl, worksheetId, "/file");
    }

    public static String image(String storageUrl, Long worksheetId, int pageNumber) {
        return endpoint(storageUrl, worksheetId, "/pages/" + pageNumber + "/image");
    }

    private static String endpoint(String storageUrl, Long worksheetId, String suffix) {
        int marker = storageUrl.indexOf("/files/");
        // 이전 버전의 외부 URL은 자동으로 다른 서버의 API URL로 바꾸지 않는다.
        if (marker < 0) return storageUrl;
        return storageUrl.substring(0, marker) + "/api/v1/worksheets/" + worksheetId + suffix;
    }
}
