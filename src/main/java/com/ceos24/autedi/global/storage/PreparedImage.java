package com.ceos24.autedi.global.storage;

/** 방향 보정과 메타데이터 제거를 마친 단일 PNG. */
public record PreparedImage(byte[] png, int width, int height, int orientation) {
    public PreparedImage {
        png = png.clone();
    }

    @Override
    public byte[] png() {
        return png.clone();
    }
}
