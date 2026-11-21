package com.ceos24.autedi.aiexperiments;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ceos24.autedi.global.storage.WorksheetImagePreparer;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.awt.image.BufferedImage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorksheetImagePreparerOrientEquivalenceTest {

    private static final int[] TYPES = {
        BufferedImage.TYPE_3BYTE_BGR,
        BufferedImage.TYPE_BYTE_GRAY,
        BufferedImage.TYPE_INT_RGB,
        BufferedImage.TYPE_INT_ARGB,
        BufferedImage.TYPE_4BYTE_ABGR
    };

    private static final int[][] SIZES = {{1, 1}, {2, 3}, {3, 2}, {5, 7}, {8, 8}, {13, 4}};

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void orientMatchesReferenceForAllTypesAndSizes(int orientation) {
        for (int type : TYPES) {
            for (int[] size : SIZES) {
                BufferedImage raw = convert(TestWorksheetImages.pattern(size[0], size[1], true), type);
                BufferedImage expected = OcrTestImage.orient(raw, orientation);
                BufferedImage actual = WorksheetImagePreparer.orient(raw, orientation);

                String label = "orientation=" + orientation + ", type=" + type + ", size=" + size[0] + "x" + size[1];
                assertEquals(expected.getWidth(), actual.getWidth(), label);
                assertEquals(expected.getHeight(), actual.getHeight(), label);
                for (int y = 0; y < expected.getHeight(); y++) {
                    for (int x = 0; x < expected.getWidth(); x++) {
                        assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), label + ", (" + x + "," + y + ")");
                    }
                }
            }
        }
    }

    /** 같은 내용을 지정한 이미지 형태로 옮겨 담는다. 정답지와 서버 함수에는 똑같은 이미지를 준다. */
    private static BufferedImage convert(BufferedImage source, int type) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), type);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                result.setRGB(x, y, source.getRGB(x, y));
            }
        }
        return result;
    }
}
