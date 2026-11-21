package com.ceos24.autedi.global.storage;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import lombok.extern.slf4j.Slf4j;

/** 기존 OcrTestImage의 EXIF/eXIf·8방향 변환을 서비스 입력에 재사용한다. 원본 바이트는 바꾸지 않는다. */
@Slf4j
public final class WorksheetImagePreparer {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    public static final long MAX_PIXELS = 40_000_000L;
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    public PreparedImage prepare(byte[] bytes, FileType type) throws IOException {
        long startNs = System.nanoTime();
        if (bytes.length == 0) throw new IllegalArgumentException("빈 이미지입니다.");
        if (bytes.length > MAX_BYTES) throw new ImageLimitException("이미지는 20MB 이하여야 합니다.");
        boolean jpeg =
                bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
        boolean png = isPng(bytes);
        if (!(jpeg && (type == FileType.JPG || type == FileType.JPEG) || png && type == FileType.PNG)) {
            throw new IllegalArgumentException("확장자와 실제 JPG/JPEG·PNG 형식이 일치해야 합니다.");
        }
        long headerDoneNs = 0L;
        long sizeDoneNs = 0L;
        long countDoneNs = 0L;
        long gcStartMs = gcMillis();
        long heapStartMb = usedHeapMb();
        long gcHeaderMs = 0L;
        BufferedImage raw;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                sizeDoneNs = System.nanoTime();
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS)
                    throw new ImageLimitException("이미지 해상도 제한은 4천만 픽셀입니다.");
                int imageCount = reader.getNumImages(true);
                countDoneNs = System.nanoTime();
                if (imageCount != 1 && !(jpeg && JpegGainMapMetadata.isSupported(reader, imageCount, bytes)))
                    throw new IllegalArgumentException("한 장의 정지 이미지 또는 HDR 보조 이미지가 포함된 사진만 지원합니다.");
                headerDoneNs = System.nanoTime();
                gcHeaderMs = gcMillis();
                raw = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (raw == null) throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        long decodeDoneNs = System.nanoTime();
        long gcDecodeMs = gcMillis();
        int orientation = orientation(bytes, jpeg);
        BufferedImage upright = orient(raw, orientation);
        long orientDoneNs = System.nanoTime();
        long gcOrientMs = gcMillis();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(upright, "png", out)) throw new IOException("PNG 인코더가 없습니다.");
        long encodeDoneNs = System.nanoTime();
        long gcEncodeMs = gcMillis();
        if (out.size() > MAX_BYTES) throw new ImageLimitException("보정 PNG가 20MB를 넘습니다.");
        log.info(
                "[upload-timing] prepare: in={}KB, pixels={}x{}, orientation={}, header={}ms(size={}, numImages={}, gainMapCheck={}), decode={}ms, orient={}ms,"
                        + " pngEncode={}ms, pngOut={}KB, total={}ms",
                bytes.length / 1024,
                upright.getWidth(),
                upright.getHeight(),
                orientation,
                ms(startNs, headerDoneNs),
                ms(startNs, sizeDoneNs),
                ms(sizeDoneNs, countDoneNs),
                ms(countDoneNs, headerDoneNs),
                ms(headerDoneNs, decodeDoneNs),
                ms(decodeDoneNs, orientDoneNs),
                ms(orientDoneNs, encodeDoneNs),
                out.size() / 1024,
                ms(startNs, encodeDoneNs));
        log.info(
                "[upload-timing] gc: header={}ms, decode={}ms, orient={}ms, pngEncode={}ms, total={}ms,"
                        + " heapUsedStart={}MB, heapUsedEnd={}MB, heapMax={}MB",
                gcHeaderMs - gcStartMs,
                gcDecodeMs - gcHeaderMs,
                gcOrientMs - gcDecodeMs,
                gcEncodeMs - gcOrientMs,
                gcEncodeMs - gcStartMs,
                heapStartMb,
                usedHeapMb(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
        return new PreparedImage(out.toByteArray(), upright.getWidth(), upright.getHeight(), orientation);
    }

    private static long ms(long fromNs, long toNs) {
        return (toNs - fromNs) / 1_000_000L;
    }

    /** 지금까지 GC에 쓴 누적 시간(ms). 구간 앞뒤의 차이로 그 구간의 GC 시간을 본다. */
    private static long gcMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0L, bean.getCollectionTime());
        }
        return total;
    }

    private static long usedHeapMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
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
        return isPlainBgr(raw) ? orientBgr(raw, orientation) : orientGeneric(raw, orientation);
    }

    /** 일반 컬러 JPEG가 읽히는 형태(한 픽셀 3바이트, 한 줄 가로x3바이트, 시작 위치 0)일 때만 true. 아니면 기존 경로를 쓴다. */
    private static boolean isPlainBgr(BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) return false;
        WritableRaster raster = image.getRaster();
        return raster.getDataBuffer() instanceof DataBufferByte buffer
                && buffer.getOffset() == 0
                && raster.getSampleModelTranslateX() == 0
                && raster.getSampleModelTranslateY() == 0
                && raster.getSampleModel() instanceof ComponentSampleModel model
                && model.getPixelStride() == 3
                && model.getScanlineStride() == image.getWidth() * 3
                && buffer.getData().length == image.getWidth() * image.getHeight() * 3;
    }

    /** 배열을 직접 읽고 써서 기존 orientGeneric과 같은 결과(알파 255의 ARGB)를 낸다. 새 번호 = start + 열 * stepX + 행 * stepY. */
    private static BufferedImage orientBgr(BufferedImage raw, int orientation) {
        int w = raw.getWidth(), h = raw.getHeight();
        byte[] src = ((DataBufferByte) raw.getRaster().getDataBuffer()).getData();
        long allocStartNs = System.nanoTime();
        BufferedImage result =
                new BufferedImage(orientation >= 5 ? h : w, orientation >= 5 ? w : h, BufferedImage.TYPE_INT_ARGB);
        int[] dst = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();
        long allocDoneNs = System.nanoTime();

        int start;
        int stepX;
        int stepY;
        switch (orientation) {
            case 1 -> {
                start = 0;
                stepX = 1;
                stepY = w;
            }
            case 2 -> {
                start = w - 1;
                stepX = -1;
                stepY = w;
            }
            case 3 -> {
                start = w * h - 1;
                stepX = -1;
                stepY = -w;
            }
            case 4 -> {
                start = (h - 1) * w;
                stepX = 1;
                stepY = -w;
            }
            case 5 -> {
                start = 0;
                stepX = h;
                stepY = 1;
            }
            case 6 -> {
                start = h - 1;
                stepX = h;
                stepY = -1;
            }
            case 7 -> {
                start = w * h - 1;
                stepX = -h;
                stepY = -1;
            }
            default -> {
                start = (w - 1) * h;
                stepX = -h;
                stepY = 1;
            }
        }

        int s = 0; // 원본에서 지금 읽는 바이트 위치(파랑, 초록, 빨강 순서)
        for (int y = 0; y < h; y++) {
            int d = start + y * stepY; // 이 행의 첫 픽셀이 가는 새 번호
            for (int x = 0; x < w; x++) {
                dst[d] = 0xFF000000 | (src[s + 2] & 0xFF) << 16 | (src[s + 1] & 0xFF) << 8 | (src[s] & 0xFF);
                s += 3;
                d += stepX;
            }
        }
        log.info(
                "[upload-timing] orient: alloc={}ms, loop={}ms",
                ms(allocStartNs, allocDoneNs),
                ms(allocDoneNs, System.nanoTime()));
        return result;
    }

    private static BufferedImage orientGeneric(BufferedImage raw, int orientation) {
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
