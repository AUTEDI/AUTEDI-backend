package com.ceos24.autedi.aiexperiments;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** 실험 공통 이미지 처리: EXIF/eXIf 8개 방향을 반영하고 메타데이터 없는 PNG를 생성합니다. API 호출 없음. */
final class OcrTestImage {
    record Prepared(BufferedImage image, byte[] png, int orientation) {}

    static Prepared read(Path path) throws Exception {
        if (Files.size(path) > 20 * 1024 * 1024) throw new IllegalArgumentException("이미지 제한: 20MB");
        byte[] bytes = Files.readAllBytes(path);
        boolean jpeg = bytes.length >= 2 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216;
        boolean png = bytes.length >= 8 && bytes[0] == (byte) 137 && bytes[1] == 80
                && bytes[2] == 78 && bytes[3] == 71;
        if (!jpeg && !png) throw new IllegalArgumentException("JPG/PNG만 지원합니다.");
        BufferedImage raw = ImageIO.read(new ByteArrayInputStream(bytes));
        if (raw == null) throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        int orientation = orientation(bytes, jpeg);
        BufferedImage upright = orient(raw, orientation);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(upright, "png", out);
        if (out.size() > 20 * 1024 * 1024) throw new IllegalArgumentException("회전 처리된 PNG가 20MB를 넘습니다.");
        return new Prepared(upright, out.toByteArray(), orientation);
    }

    static int orientation(byte[] bytes, boolean jpeg) {
        if (jpeg) {
            int pos = 2;
            while (pos + 4 <= bytes.length && (bytes[pos] & 255) == 255) {
                int marker = bytes[pos + 1] & 255;
                if (marker == 218 || marker == 217) break;
                int length = ((bytes[pos + 2] & 255) << 8) | (bytes[pos + 3] & 255);
                if (length < 2 || pos + 2L + length > bytes.length) break;
                if (marker == 225 && length >= 8 && bytes[pos + 4] == 'E'
                        && bytes[pos + 5] == 'x' && bytes[pos + 6] == 'i' && bytes[pos + 7] == 'f'
                        && bytes[pos + 8] == 0 && bytes[pos + 9] == 0) {
                    return tiffOrientation(bytes, pos + 10, length - 8);
                }
                pos += 2 + length;
            }
        } else {
            // PNG의 eXIf 청크가 있으면 같은 TIFF 방향 정보를 사용합니다.
            int pos = 8;
            while (pos + 12 <= bytes.length) {
                int length = ByteBuffer.wrap(bytes, pos, 4).getInt();
                if (length < 0 || pos + 12L + length > bytes.length) break;
                if (bytes[pos + 4] == 'e' && bytes[pos + 5] == 'X'
                        && bytes[pos + 6] == 'I' && bytes[pos + 7] == 'f') {
                    return tiffOrientation(bytes, pos + 8, length);
                }
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

    static BufferedImage orient(BufferedImage raw, int orientation) {
        if (orientation < 1 || orientation > 8) throw new IllegalArgumentException("잘못된 방향입니다.");
        int w = raw.getWidth(), h = raw.getHeight();
        BufferedImage result = new BufferedImage(orientation >= 5 ? h : w,
                orientation >= 5 ? w : h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int dx = switch (orientation) {
                case 2, 3 -> w - 1 - x;
                case 5, 8 -> y;
                case 6, 7 -> h - 1 - y;
                default -> x;
            };
            int dy = switch (orientation) {
                case 3, 4 -> h - 1 - y;
                case 5, 6 -> x;
                case 7, 8 -> w - 1 - x;
                default -> y;
            };
            result.setRGB(dx, dy, raw.getRGB(x, y));
        }
        return result;
    }
}
