package com.ceos24.autedi.global.util;

import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QrCodeGenerator {

    private static final String DATA_URI_PREFIX = "data:image/png;base64,";

    public byte[] toPng(String content, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(
                            content,
                            BarcodeFormat.QR_CODE,
                            size,
                            size,
                            Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            // content(토큰 포함)를 예외 메시지에 남기지 않는다.
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public String toDataUri(String content, int size) {
        return DATA_URI_PREFIX + Base64.getEncoder().encodeToString(toPng(content, size));
    }
}
