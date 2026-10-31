package com.ceos24.autedi.domain.worksheet.service;

/** 화면 조회와 향후 전사·첨삭이 공유하는 저장 PNG 스냅샷. 두 AI 단계에 이 객체의 같은 png를 사용한다. */
public record PreparedWorksheetImage(
        Long worksheetId,
        Long pageId,
        int pageNumber,
        String imageUrl,
        int width,
        int height,
        String sha256,
        byte[] png) {
    public PreparedWorksheetImage {
        png = png.clone();
    }

    @Override
    public byte[] png() {
        return png.clone();
    }
}
