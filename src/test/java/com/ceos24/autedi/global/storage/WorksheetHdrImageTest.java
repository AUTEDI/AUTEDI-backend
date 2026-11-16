package com.ceos24.autedi.global.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorksheetHdrImageTest {
    private final WorksheetImagePreparer preparer = new WorksheetImagePreparer();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void usesOnlyPrimaryPixelsAndOrientationAndPreservesOriginal(boolean adobe) throws Exception {
        byte[] source = TestWorksheetImages.hdrJpeg(adobe);
        byte[] original = source.clone();
        var expected = WorksheetImagePreparer.orient(ImageIO.read(new ByteArrayInputStream(source)), 6);
        PreparedImage result = preparer.prepare(source, FileType.JPEG);
        var png = ImageIO.read(new ByteArrayInputStream(result.png()));
        assertEquals(3, result.width());
        assertEquals(2, result.height());
        assertEquals(6, result.orientation());
        for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) assertEquals(expected.getRGB(x, y), png.getRGB(x, y));
        assertEquals(1, WorksheetImagePreparer.orientation(result.png(), false));
        assertArrayEquals(original, source);
    }

    @Test
    void acceptsGalaxyStyleHdrWhoseAuxiliaryImageHasJfifAfterXmp() throws Exception {
        byte[] source = TestWorksheetImages.hdrJpegWithJfifAfterXmpInAuxiliary();
        // 이 구조는 ImageIO가 보조 이미지 메타데이터를 읽지 못하므로, 바이트 직접 판독으로 HDR 여부를 판단해야 한다.
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            var reader = ImageIO.getImageReaders(input).next();
            try {
                reader.setInput(input, false, false);
                assertEquals(2, reader.getNumImages(true));
                assertThrows(javax.imageio.IIOException.class, () -> reader.getImageMetadata(1));
            } finally {
                reader.dispose();
            }
        }
        PreparedImage result = preparer.prepare(source, FileType.JPEG);
        assertEquals(3, result.width());
        assertEquals(2, result.height());
    }

    @Test
    void rejectsUnidentifiedPhotosAndExtraImagesInsteadOfGuessing() throws Exception {
        String depth = TestWorksheetImages.xmp(
                "xmlns:p='http://ns.apple.com/pixeldatainfo/1.0/'",
                "<p:AuxiliaryImageType>urn:com:apple:photo:2020:aux:depth</p:AuxiliaryImageType>");
        String wrongNamespace = TestWorksheetImages.xmp(
                "xmlns:p='urn:unrelated'",
                "<p:AuxiliaryImageType>urn:com:apple:photo:2020:aux:hdrgainmap</p:AuxiliaryImageType>");
        for (String metadata : new String[] {null, depth, wrongNamespace}) {
            byte[] image = TestWorksheetImages.jpegWithAuxiliary(null, metadata);
            assertThrows(IllegalArgumentException.class, () -> preparer.prepare(image, FileType.JPG));
        }
        ByteArrayOutputStream extra = new ByteArrayOutputStream();
        extra.write(TestWorksheetImages.hdrJpeg(false));
        extra.write(TestWorksheetImages.withOrientation(true, 1, ByteOrder.BIG_ENDIAN));
        assertThrows(IllegalArgumentException.class, () -> preparer.prepare(extra.toByteArray(), FileType.JPG));
    }

    @Test
    void rejectsMalformedXmpAndExternalEntitiesWithoutReadingExternalResources() throws Exception {
        String entity = "<!DOCTYPE x [<!ENTITY external SYSTEM 'http://127.0.0.1:1/never-read'>]>"
                + TestWorksheetImages.xmp(
                        "xmlns:p='http://ns.apple.com/pixeldatainfo/1.0/'",
                        "<p:AuxiliaryImageType>&external;</p:AuxiliaryImageType>");
        for (String metadata : new String[] {"<broken", entity}) {
            byte[] image = TestWorksheetImages.jpegWithAuxiliary(null, metadata);
            assertThrows(IllegalArgumentException.class, () -> preparer.prepare(image, FileType.JPG));
        }
    }

    @Test
    void rejectsHdrBaseThatWouldRequireGainMapRenderingToProduceSdr() throws Exception {
        String hdrBase = TestWorksheetImages.xmp(
                "xmlns:g='http://ns.adobe.com/hdr-gain-map/1.0/' g:Version='1.0' g:BaseRenditionIsHDR='True'", "");
        byte[] image = TestWorksheetImages.jpegWithAuxiliary(hdrBase, hdrBase);
        assertThrows(IllegalArgumentException.class, () -> preparer.prepare(image, FileType.JPG));
    }
}
