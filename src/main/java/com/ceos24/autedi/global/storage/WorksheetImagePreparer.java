package com.ceos24.autedi.global.storage;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

/** 기존 OcrTestImage의 EXIF/eXIf·8방향 변환을 서비스 입력에 재사용한다. 원본 바이트는 바꾸지 않는다. */
public final class WorksheetImagePreparer {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    public static final long MAX_PIXELS = 40_000_000L;
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    public PreparedImage prepare(byte[] bytes, FileType type) throws IOException {
        if (bytes.length == 0) throw new IllegalArgumentException("빈 이미지입니다.");
        if (bytes.length > MAX_BYTES) throw new ImageLimitException("이미지는 20MB 이하여야 합니다.");
        boolean jpeg =
                bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
        boolean png = isPng(bytes);
        if (!(jpeg && (type == FileType.JPG || type == FileType.JPEG) || png && type == FileType.PNG)) {
            throw new IllegalArgumentException("확장자와 실제 JPG/JPEG·PNG 형식이 일치해야 합니다.");
        }
        BufferedImage raw;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS)
                    throw new ImageLimitException("이미지 해상도 제한은 4천만 픽셀입니다.");
                if (reader.getNumImages(true) != 1) throw new IllegalArgumentException("한 장의 정지 이미지만 지원합니다.");
                raw = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (raw == null) throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        int orientation = orientation(bytes, jpeg);
        BufferedImage upright = orient(raw, orientation);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(upright, "png", out)) throw new IOException("PNG 인코더가 없습니다.");
        if (out.size() > MAX_BYTES) throw new ImageLimitException("보정 PNG가 20MB를 넘습니다.");
        return new PreparedImage(out.toByteArray(), upright.getWidth(), upright.getHeight(), orientation);
    }

    public static boolean isPng(byte[] bytes) {
        return bytes.length >= 8 && Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(bytes, 8));
    }

    public static final class ImageLimitException extends IllegalArgumentException {
        public ImageLimitException(String message) {
            super(message);
        }
    }

    public static int orientation(byte[] bytes, boolean jpeg) {
        if (jpeg) {
            int pos = 2;
            while (pos + 4 <= bytes.length && (bytes[pos] & 255) == 255) {
                int marker = bytes[pos + 1] & 255;
                if (marker == 218 || marker == 217) break;
                int length = ((bytes[pos + 2] & 255) << 8) | (bytes[pos + 3] & 255);
                if (length < 2 || pos + 2L + length > bytes.length) break;
                if (marker == 225
                        && length >= 8
                        && bytes[pos + 4] == 'E'
                        && bytes[pos + 5] == 'x'
                        && bytes[pos + 6] == 'i'
                        && bytes[pos + 7] == 'f'
                        && bytes[pos + 8] == 0
                        && bytes[pos + 9] == 0) {
                    return tiffOrientation(bytes, pos + 10, length - 8);
                }
                pos += 2 + length;
            }
        } else {
            int pos = 8;
            while (pos + 12 <= bytes.length) {
                int length = ByteBuffer.wrap(bytes, pos, 4).getInt();
                if (length < 0 || pos + 12L + length > bytes.length) break;
                if (bytes[pos + 4] == 'e' && bytes[pos + 5] == 'X' && bytes[pos + 6] == 'I' && bytes[pos + 7] == 'f')
                    return tiffOrientation(bytes, pos + 8, length);
                pos += length + 12;
            }
        }
        return 1;
    }

    private static int tiffOrientation(byte[] bytes, int offset, int length) {
        if (length < 8) throw new IllegalArgumentException("손상된 EXIF 헤더입니다.");
        ByteBuffer tiff = ByteBuffer.wrap(bytes, offset, length).slice();
        if (tiff.get(0) == 'I' && tiff.get(1) == 'I') tiff.order(ByteOrder.LITTLE_ENDIAN);
        else if (tiff.get(0) == 'M' && tiff.get(1) == 'M') tiff.order(ByteOrder.BIG_ENDIAN);
        else throw new IllegalArgumentException("지원하지 않는 EXIF 바이트 순서입니다.");
        if (tiff.getShort(2) != 42) throw new IllegalArgumentException("손상된 TIFF 헤더입니다.");
        int ifd = tiff.getInt(4);
        if (ifd < 0 || ifd + 2L > length) throw new IllegalArgumentException("손상된 EXIF 위치입니다.");
        int count = Short.toUnsignedInt(tiff.getShort(ifd));
        for (int i = 0; i < count; i++) {
            long entry = ifd + 2L + i * 12L;
            if (entry + 12 > length) throw new IllegalArgumentException("손상된 EXIF 항목입니다.");
            int p = (int) entry;
            if (Short.toUnsignedInt(tiff.getShort(p)) == 274) {
                if (tiff.getShort(p + 2) != 3 || tiff.getInt(p + 4) != 1)
                    throw new IllegalArgumentException("지원하지 않는 EXIF 방향 형식입니다.");
                int value = Short.toUnsignedInt(tiff.getShort(p + 8));
                if (value < 1 || value > 8) throw new IllegalArgumentException("잘못된 EXIF 방향입니다.");
                return value;
            }
        }
        return 1;
    }

    public static BufferedImage orient(BufferedImage raw, int orientation) {
        if (orientation < 1 || orientation > 8) throw new IllegalArgumentException("잘못된 방향입니다.");
        int w = raw.getWidth(), h = raw.getHeight();
        BufferedImage result =
                new BufferedImage(orientation >= 5 ? h : w, orientation >= 5 ? w : h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx =
                        switch (orientation) {
                            case 2, 3 -> w - 1 - x;
                            case 5, 8 -> y;
                            case 6, 7 -> h - 1 - y;
                            default -> x;
                        };
                int dy =
                        switch (orientation) {
                            case 3, 4 -> h - 1 - y;
                            case 5, 6 -> x;
                            case 7, 8 -> w - 1 - x;
                            default -> y;
                        };
                result.setRGB(dx, dy, raw.getRGB(x, y));
            }
        }
        return result;
    }
}
