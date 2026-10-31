package com.ceos24.autedi.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** 외부 사진이나 API 없이 JPEG EXIF·PNG eXIf를 포함하는 작은 고정 이미지를 만든다. */
public final class TestWorksheetImages {
    private TestWorksheetImages() {}

    public static BufferedImage pattern(int width, int height, boolean alpha) {
        BufferedImage image =
                new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int opacity = alpha ? (80 + 20 * (x + y)) % 256 : 255;
                image.setRGB(
                        x, y, opacity << 24 | ((40 + x * 37) % 256) << 16 | ((30 + y * 51) % 256) << 8 | (15 + x + y));
            }
        }
        return image;
    }

    public static byte[] png(int width, int height) throws IOException {
        return encode(pattern(width, height, true), "png");
    }

    public static byte[] withOrientation(boolean jpeg, int orientation, ByteOrder order) throws IOException {
        byte[] source = encode(pattern(2, 3, !jpeg), jpeg ? "jpeg" : "png");
        ByteBuffer tiff = ByteBuffer.allocate(26).order(order);
        tiff.put((byte) (order == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
        tiff.put(tiff.get(0));
        tiff.putShort((short) 42).putInt(8).putShort((short) 1);
        tiff.putShort((short) 274)
                .putShort((short) 3)
                .putInt(1)
                .putShort((short) orientation)
                .putShort((short) 0)
                .putInt(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (jpeg) {
            out.write(source, 0, 2);
            out.write(new byte[] {(byte) 255, (byte) 225, 0, 34, 'E', 'x', 'i', 'f', 0, 0});
            out.write(tiff.array());
            out.write(source, 2, source.length - 2);
        } else {
            out.write(source, 0, 33);
            byte[] type = "eXIf".getBytes(StandardCharsets.US_ASCII);
            out.write(ByteBuffer.allocate(4).putInt(tiff.capacity()).array());
            out.write(type);
            out.write(tiff.array());
            CRC32 crc = new CRC32();
            crc.update(type);
            crc.update(tiff.array());
            out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
            out.write(source, 33, source.length - 33);
        }
        return out.toByteArray();
    }

    public static byte[] oversizedHeader() throws IOException {
        byte[] png = png(2, 3);
        ByteBuffer.wrap(png).putInt(16, 8000).putInt(20, 6000);
        CRC32 crc = new CRC32();
        crc.update(Arrays.copyOfRange(png, 12, 29));
        ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
        return png;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) throw new IOException("테스트 이미지 인코더가 없습니다.");
        return out.toByteArray();
    }
}
