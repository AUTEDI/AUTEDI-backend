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

    public static byte[] hdrJpeg(boolean adobe) throws IOException {
        String adobeXmp =
                xmp("xmlns:g='http://ns.adobe.com/hdr-gain-map/1.0/' g:Version='1.0' g:BaseRenditionIsHDR='False'", "");
        String appleXmp = xmp(
                "xmlns:p='http://ns.apple.com/pixeldatainfo/1.0/'",
                "<p:AuxiliaryImageType>urn:com:apple:photo:2020:aux:hdrgainmap</p:AuxiliaryImageType>");
        return jpegWithAuxiliary(adobe ? adobeXmp : null, adobe ? adobeXmp : appleXmp);
    }

    /** 실제 MPF 인덱스와 별도 XMP를 가진 두 JPEG. 기본 사진은 2×3·EXIF 6, 보조 이미지는 1×1이다. */
    public static byte[] jpegWithAuxiliary(String primaryXmp, String auxiliaryXmp) throws IOException {
        byte[] primary = withoutJfif(withOrientation(true, 6, ByteOrder.BIG_ENDIAN));
        if (primaryXmp != null) primary = withXmp(primary, primaryXmp);
        byte[] auxiliary = withoutJfif(encode(pattern(1, 1, false), "jpeg"));
        if (auxiliaryXmp != null) auxiliary = withXmp(auxiliary, auxiliaryXmp);
        int primaryLength = primary.length + 90;
        ByteBuffer mpf = ByteBuffer.allocate(86);
        mpf.put(new byte[] {'M', 'P', 'F', 0, 'M', 'M'})
                .putShort((short) 42)
                .putInt(8)
                .putShort((short) 3);
        mpf.putShort((short) 0xb000).putShort((short) 7).putInt(4).put(new byte[] {'0', '1', '0', '0'});
        mpf.putShort((short) 0xb001).putShort((short) 4).putInt(1).putInt(2);
        mpf.putShort((short) 0xb002).putShort((short) 7).putInt(32).putInt(50).putInt(0);
        mpf.putInt(0x030000).putInt(primaryLength).putInt(0).putInt(0);
        mpf.putInt(0).putInt(auxiliary.length).putInt(primaryLength - 10).putInt(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(withJpegSegment(primary, 0xe2, mpf.array()));
        out.write(auxiliary);
        return out.toByteArray();
    }

    public static String xmp(String attributes, String content) {
        return "<x:xmpmeta xmlns:x='adobe:ns:meta/'><r:RDF xmlns:r='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>"
                + "<r:Description " + attributes + ">" + content + "</r:Description></r:RDF></x:xmpmeta>";
    }

    private static byte[] withXmp(byte[] jpeg, String xml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII));
        out.write(xml.getBytes(StandardCharsets.UTF_8));
        return withJpegSegment(jpeg, 0xe1, out.toByteArray());
    }

    private static byte[] withJpegSegment(byte[] jpeg, int marker, byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(255);
        out.write(marker);
        out.write(ByteBuffer.allocate(2).putShort((short) (payload.length + 2)).array());
        out.write(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** EXIF JPEG로 구성한다. JFIF APP0를 뒤로 밀면 ImageIO의 메타데이터 판독에서 거부된다. */
    private static byte[] withoutJfif(byte[] jpeg) throws IOException {
        int pos = 2;
        while ((jpeg[pos] & 255) == 255 && (jpeg[pos + 1] & 255) != 0xda) {
            int length = ((jpeg[pos + 2] & 255) << 8) | (jpeg[pos + 3] & 255);
            if ((jpeg[pos + 1] & 255) == 0xe0) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(jpeg, 0, pos);
                out.write(jpeg, pos + 2 + length, jpeg.length - pos - 2 - length);
                return out.toByteArray();
            }
            pos += 2 + length;
        }
        return jpeg;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) throw new IOException("테스트 이미지 인코더가 없습니다.");
        return out.toByteArray();
    }
}
