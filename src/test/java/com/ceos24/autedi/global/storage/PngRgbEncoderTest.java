package com.ceos24.autedi.global.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteOrder;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PngRgbEncoderTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    @DisplayName("직접 만든 PNG를 JDK로 다시 읽으면 크기와 RGB가 같고 알파 채널이 없다")
    void decodedPixelsMatchForSeveralSizes(int seed) throws Exception {
        int[][] sizes = {{1, 1}, {2, 3}, {3, 2}, {5, 7}, {13, 4}, {64, 64}, {255, 1}, {1, 255}};
        int[] size = sizes[seed - 1];
        BufferedImage source = opaqueImage(size[0], size[1], seed);

        BufferedImage decoded = decode(PngRgbEncoder.encode(source));

        assertEquals(size[0], decoded.getWidth());
        assertEquals(size[1], decoded.getHeight());
        assertFalse(decoded.getColorModel().hasAlpha());
        assertSamePixels(source, decoded);
    }

    @Test
    @DisplayName("압축 결과가 1MB 조각 경계를 넘는 큰 이미지도 다시 읽으면 같다")
    void imageLargerThanOneChunkRoundTrips() throws Exception {
        BufferedImage source = new BufferedImage(700, 700, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(11);
        for (int y = 0; y < 700; y++) {
            for (int x = 0; x < 700; x++) source.setRGB(x, y, 0xFF000000 | random.nextInt(0x1000000));
        }

        byte[] png = PngRgbEncoder.encode(source);

        assertTrue(png.length > (1 << 20), "조각이 둘 이상이어야 한다: " + png.length);
        assertSamePixels(source, decode(png));
    }

    @Test
    @DisplayName("압축 레벨이 달라도 픽셀은 같다")
    void everyCompressionLevelKeepsPixels() throws Exception {
        BufferedImage source = opaqueImage(40, 30, 5);
        for (int level = 0; level <= 9; level++) {
            assertSamePixels(source, decode(PngRgbEncoder.encode(source, level)));
        }
    }

    @Test
    @DisplayName("ARGB 배열이 아닌 이미지는 지원하지 않는다")
    void unsupportedImageTypeIsRejected() {
        BufferedImage rgb = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);

        assertFalse(PngRgbEncoder.supports(rgb));
        assertThrows(IllegalArgumentException.class, () -> PngRgbEncoder.encode(rgb));
    }

    @Test
    @DisplayName("컬러 JPEG를 보정하면 알파 없는 RGB PNG가 되고 방향과 픽셀은 기존 보정과 같다")
    void preparedColorJpegIsRgbPngWithSamePixels() throws Exception {
        byte[] source = TestWorksheetImages.withOrientation(true, 6, ByteOrder.LITTLE_ENDIAN);
        BufferedImage expected = WorksheetImagePreparer.orient(ImageIO.read(new ByteArrayInputStream(source)), 6);

        PreparedImage prepared = new WorksheetImagePreparer().prepare(source, FileType.JPEG);
        BufferedImage actual = decode(prepared.png());

        assertFalse(actual.getColorModel().hasAlpha());
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        assertSamePixels(expected, actual);
    }

    private static BufferedImage opaqueImage(int width, int height, int seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 가로로 번지는 값과 약간의 노이즈를 섞어 Up 필터가 값을 음수로도 만들게 한다.
                int r = (x * 7 + random.nextInt(20)) & 255;
                int g = (y * 13 + random.nextInt(20)) & 255;
                int b = (x * y + random.nextInt(255)) & 255;
                image.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    private static BufferedImage decode(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static void assertSamePixels(BufferedImage expected, BufferedImage actual) {
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y) & 0xFFFFFF, actual.getRGB(x, y) & 0xFFFFFF, "(" + x + "," + y + ")");
            }
        }
    }
}
