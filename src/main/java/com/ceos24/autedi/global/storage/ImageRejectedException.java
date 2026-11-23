package com.ceos24.autedi.global.storage;

import lombok.Getter;

/** 이미지 검사에서 거부된 이유를 코드와 함께 담는다. 메시지는 기존 IllegalArgumentException 문구와 같다. */
@Getter
public class ImageRejectedException extends IllegalArgumentException {

    private final ImageRejectReason reason;

    public ImageRejectedException(ImageRejectReason reason) {
        super(reason.getMessage());
        this.reason = reason;
    }
}
