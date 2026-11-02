package com.ceos24.autedi.aiexperiments;

import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.global.storage.PreparedImage;
import com.ceos24.autedi.global.storage.WorksheetImagePreparer;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.io.ByteArrayInputStream;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorksheetImagePreparerCompatibilityTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void jpegAndPngBothByteOrdersMatchExistingOrientationAndPixels(int orientation) throws Exception {
        for (boolean jpeg : new boolean[] {true, false}) {
            for (ByteOrder order : new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
                byte[] source = TestWorksheetImages.withOrientation(jpeg, orientation, order);
                byte[] original = source.clone();
                assertEquals(orientation, OcrTestImage.orientation(source, jpeg));
                assertEquals(orientation, WorksheetImagePreparer.orientation(source, jpeg));
                var raw = ImageIO.read(new ByteArrayInputStream(source));
                var expected = OcrTestImage.orient(raw, orientation);
                PreparedImage prepared =
                        new WorksheetImagePreparer().prepare(source, jpeg ? FileType.JPEG : FileType.PNG);
                var actual = ImageIO.read(new ByteArrayInputStream(prepared.png()));
                assertEquals(expected.getWidth(), prepared.width());
                assertEquals(expected.getHeight(), prepared.height());
                assertEquals(orientation, prepared.orientation());
                assertEquals(1, WorksheetImagePreparer.orientation(prepared.png(), false));
                for (int y = 0; y < expected.getHeight(); y++) {
                    for (int x = 0; x < expected.getWidth(); x++)
                        assertEquals(expected.getRGB(x, y), actual.getRGB(x, y));
                }
                assertArrayEquals(original, source);
                byte[] copy = prepared.png();
                copy[0] = 0;
                assertTrue(WorksheetImagePreparer.isPng(prepared.png()));
            }
        }
    }
}
