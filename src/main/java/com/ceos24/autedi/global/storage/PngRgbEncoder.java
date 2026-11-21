package com.ceos24.autedi.global.storage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 알파가 필요 없는(항상 불투명한) 이미지를 RGB PNG로 직접 인코딩한다. 한 줄씩 필터링해 압축하므로 JDK PNG 인코더처럼 이미지 크기의 몇 배를 한꺼번에 할당하지 않는다.
 * 필터는 위쪽 줄과의 차이(Up)만 쓴다. 압축 레벨은 속도와 파일 크기의 맞교환이다.
 */
public final class PngRgbEncoder {
    public static final int DEFAULT_LEVEL = 2;
    private static final int CHUNK_BYTES = 1 << 20;
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final byte[] IDAT = "IDAT".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] IHDR = "IHDR".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] IEND = "IEND".getBytes(StandardCharsets.US_ASCII);
    private static final byte FILTER_UP = 2;

    private PngRgbEncoder() {}

    /** TYPE_INT_ARGB이고 한 줄이 가로 픽셀 수와 같은 평범한 배열일 때만 true. */
    public static boolean supports(BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) return false;
        var raster = image.getRaster();
        return raster.getDataBuffer() instanceof DataBufferInt buffer
                && buffer.getOffset() == 0
                && raster.getSampleModelTranslateX() == 0
                && raster.getSampleModelTranslateY() == 0
                && raster.getSampleModel() instanceof SinglePixelPackedSampleModel model
                && model.getScanlineStride() == image.getWidth()
                && buffer.getData().length == image.getWidth() * image.getHeight();
    }

    public static byte[] encode(BufferedImage argb) {
        return encode(argb, DEFAULT_LEVEL);
    }

    /** 알파는 버리고 RGB만 쓴다. 호출하는 쪽이 이미지가 불투명하다는 것을 보장해야 한다. */
    public static byte[] encode(BufferedImage argb, int level) {
        if (!supports(argb)) throw new IllegalArgumentException("지원하지 않는 이미지 형태입니다.");
        int w = argb.getWidth(), h = argb.getHeight();
        int[] pixels = ((DataBufferInt) argb.getRaster().getDataBuffer()).getData();
        int stride = w * 3;
        byte[] row = new byte[1 + stride];
        byte[] cur = new byte[stride];
        byte[] prev = new byte[stride];
        Deflater deflater = new Deflater(level);
        Sink sink = new Sink();
        try {
            row[0] = FILTER_UP;
            for (int y = 0; y < h; y++) {
                int base = y * w;
                for (int x = 0, i = 0; x < w; x++, i += 3) {
                    int p = pixels[base + x];
                    cur[i] = (byte) (p >> 16);
                    cur[i + 1] = (byte) (p >> 8);
                    cur[i + 2] = (byte) p;
                }
                for (int i = 0; i < stride; i++) row[1 + i] = (byte) (cur[i] - prev[i]);
                deflater.setInput(row, 0, row.length);
                sink.drain(deflater, false);
                byte[] t = prev;
                prev = cur;
                cur = t;
            }
            deflater.finish();
            sink.drain(deflater, true);
        } finally {
            deflater.end();
        }
        return sink.assemble(w, h);
    }

    /** 압축 결과를 1MB 단위 IDAT 조각으로 모은다. 조각은 만들어질 때 한 번만 할당하고 복사하지 않는다. */
    private static final class Sink {
        private final List<byte[]> chunks = new ArrayList<>();
        private byte[] buf = new byte[8 + CHUNK_BYTES + 4];
        private int len;

        void drain(Deflater deflater, boolean finish) {
            while (finish ? !deflater.finished() : !deflater.needsInput()) {
                len += deflater.deflate(buf, 8 + len, CHUNK_BYTES - len);
                if (len == CHUNK_BYTES) {
                    chunks.add(seal(buf, len));
                    buf = new byte[8 + CHUNK_BYTES + 4];
                    len = 0;
                }
            }
            if (finish && len > 0) {
                chunks.add(seal(Arrays.copyOf(buf, 8 + len + 4), len));
                len = 0;
            }
        }

        byte[] assemble(int w, int h) {
            int idatBytes = 0;
            for (byte[] c : chunks) idatBytes += c.length;
            byte[] out = new byte[SIGNATURE.length + 25 + idatBytes + 12];
            int pos = 0;
            System.arraycopy(SIGNATURE, 0, out, pos, SIGNATURE.length);
            pos += SIGNATURE.length;
            byte[] header = new byte[13 + 12];
            putInt(header, 0, 13);
            System.arraycopy(IHDR, 0, header, 4, 4);
            putInt(header, 8, w);
            putInt(header, 12, h);
            header[16] = 8; // 비트 깊이
            header[17] = 2; // 색 타입 RGB
            putInt(header, 21, crc(header, 4, 4 + 13));
            System.arraycopy(header, 0, out, pos, header.length);
            pos += header.length;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, out, pos, c.length);
                pos += c.length;
            }
            byte[] end = new byte[12];
            System.arraycopy(IEND, 0, end, 4, 4);
            putInt(end, 8, crc(end, 4, 4));
            System.arraycopy(end, 0, out, pos, end.length);
            return out;
        }

        private static byte[] seal(byte[] chunk, int dataLen) {
            putInt(chunk, 0, dataLen);
            System.arraycopy(IDAT, 0, chunk, 4, 4);
            putInt(chunk, 8 + dataLen, crc(chunk, 4, 4 + dataLen));
            return chunk;
        }

        private static int crc(byte[] data, int off, int len) {
            CRC32 crc = new CRC32();
            crc.update(data, off, len);
            return (int) crc.getValue();
        }

        private static void putInt(byte[] b, int at, int v) {
            b[at] = (byte) (v >>> 24);
            b[at + 1] = (byte) (v >>> 16);
            b[at + 2] = (byte) (v >>> 8);
            b[at + 3] = (byte) v;
        }
    }
}
