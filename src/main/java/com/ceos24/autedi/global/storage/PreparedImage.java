package com.ceos24.autedi.global.storage;

/**
 * 방향 보정과 메타데이터 제거를 마친 단일 PNG. 생성자는 배열을 복사하지 않고 소유권을 넘겨받으므로 만든 쪽이 이후 배열을 바꾸면 안 된다. 바깥으로는 항상
 * 복사본을 내보낸다.
 */
public record PreparedImage(byte[] png, int width, int height, int orientation) {
    @Override
    public byte[] png() {
        return png.clone();
    }

    /** 배열을 복사하지 않고 PNG 크기(바이트)만 돌려준다. */
    public int pngSize() {
        return png.length;
    }
}
