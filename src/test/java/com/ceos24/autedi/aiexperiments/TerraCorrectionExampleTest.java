package com.ceos24.autedi.aiexperiments;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** A 방식 로컬 단위 테스트: 원문 위치·직사각형 검증·픽셀 변환·겹침·EXIF 방향을 검사합니다. API 호출 없음. */
class TerraCorrectionExampleTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void rejectsDegenerateReversedOutOfRangeAndLegacyBoxes() {
        for (String input : List.of(
                "[1,1,1,1]",
                "[0.8,0.1,0.2,0.5]",
                "[0.1,0.8,0.5,0.2]",
                "[-0.1,0,1,1]",
                "[0,0,1.1,1]",
                "[0,0,1,0]",
                "[0,0,1]",
                "[0,0,1,0,1,1,0,1]",
                "[\"0\",0,1,1]",
                "[null,0,1,1]")) {
            assertThrows(
                    IllegalStateException.class, () -> TerraCorrectionExample.parseBox(json.readTree(input)), input);
        }
        assertEquals(List.of(0.0, 0.0, 1.0, 1.0), TerraCorrectionExample.parseBox(json.readTree("[0,0,1,1]")));
    }

    @Test
    void keepsValidBoxesAndFlagsInvalidOrMissingLocations() {
        String template =
                """
                {"edits":[{"kind":"grammar","line_id":1,"original":"go",
                "occurrence":1,"replacement":"went","reason":"tense",
                "location_uncertain":false,"boxes":%s}]}
                """;
        var item = TerraCorrectionExample.validateEdits(
                        json.readTree(template.formatted("[[0,0,0.5,0.5],[1,1,1,1],[0.5,0.5,1,1]]")),
                        List.of("go"),
                        100,
                        200)
                .getFirst();
        assertEquals(
                List.of(List.of(0.0, 0.0, 50.0, 100.0), List.of(50.0, 100.0, 100.0, 200.0)), item.get("pixel_boxes"));
        assertEquals(true, item.get("requires_review"));
        var missing = TerraCorrectionExample.validateEdits(
                        json.readTree(template.formatted("[]")), List.of("go"), 100, 200)
                .getFirst();
        assertEquals(List.of(), missing.get("pixel_boxes"));
        assertEquals(true, missing.get("requires_review"));
    }

    @Test
    void mapsRepeatedWordsAndUtf16OffsetsExactly() {
        assertEquals(8, TerraCorrectionExample.findOccurrence("go then go", "go", 2));
        assertEquals(3, TerraCorrectionExample.findOccurrence("😀 go", "go", 1));
        assertEquals(-1, TerraCorrectionExample.findOccurrence("go", "Go", 1));
        assertEquals(-1, TerraCorrectionExample.findOccurrence("go", "", 1));
        assertEquals(-1, TerraCorrectionExample.findOccurrence("go", "go", 2));
    }

    @Test
    void restoresTextOffsetsAndPixelCoordinatesFromExample() throws Exception {
        try (var stream = getClass().getResourceAsStream("/ocr/correction-response-example.json")) {
            assertNotNull(stream);
            var response = json.readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            var edits =
                    TerraCorrectionExample.validateEdits(response, List.of("He go to school yesterday."), 1000, 2000);
            var edit = edits.getFirst();
            assertEquals(3, edit.get("start"));
            assertEquals(5, edit.get("end"));
            assertEquals(List.of(List.of(300.0, 740.0, 340.0, 1040.0)), edit.get("pixel_boxes"));
            assertEquals(false, edit.get("requires_review"));
        }
    }

    @Test
    void flagsInvalidAnchorCoordinatesAndOcrMismatchWithoutApplyingCorrections() {
        var response = json.readTree(
                """
                {"edits":[{"kind":"ocr_mismatch","line_id":2,"original":"go",
                "occurrence":1,"replacement":"to","reason":"OCR 확인",
                "location_uncertain":false,"boxes":[[0,0,1,1,1,0,0,1]]}]}
                """);
        var item = TerraCorrectionExample.validateEdits(response, List.of("He go"), 100, 100)
                .getFirst();
        assertEquals(-1, item.get("start"));
        assertEquals(true, item.get("requires_review"));
        assertEquals(List.of(), item.get("boxes"));
        assertTrue(((List<?>) item.get("warnings")).size() >= 3);
    }

    @Test
    void flagsOverlappingCorrectionsAndAllowsEmptyResults() {
        String edit =
                """
                {"kind":"grammar","line_id":1,"original":"go","occurrence":1,
                "replacement":"went","reason":"시제","location_uncertain":false,
                "boxes":[[0,0,1,1]]}
                """;
        var edits = TerraCorrectionExample.validateEdits(
                json.readTree("{\"edits\":[" + edit + "," + edit + "]}"), List.of("He go"), 100, 100);
        assertTrue(edits.stream().allMatch(e -> Boolean.TRUE.equals(e.get("requires_review"))));
        assertTrue(TerraCorrectionExample.validateEdits(json.readTree("{\"edits\":[]}"), List.of("Hello."), 100, 100)
                .isEmpty());
    }

    @Test
    void readsExifOrientationInEitherByteOrder() {
        for (ByteOrder order : List.of(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            byte[] bytes = new byte[40];
            bytes[0] = (byte) 255;
            bytes[1] = (byte) 216;
            bytes[2] = (byte) 255;
            bytes[3] = (byte) 225;
            bytes[5] = 34;
            bytes[6] = 'E';
            bytes[7] = 'x';
            bytes[8] = 'i';
            bytes[9] = 'f';
            ByteBuffer tiff = ByteBuffer.wrap(bytes, 12, 26).slice().order(order);
            tiff.put(0, (byte) (order == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
            tiff.put(1, tiff.get(0));
            tiff.putShort(2, (short) 42);
            tiff.putInt(4, 8);
            tiff.putShort(8, (short) 1);
            tiff.putShort(10, (short) 274);
            tiff.putShort(12, (short) 3);
            tiff.putInt(14, 1);
            tiff.putShort(18, (short) 6);
            assertEquals(6, OcrTestImage.orientation(bytes, true));
        }
    }

    @Test
    void transformsAllEightOrientationsWithoutLosingPixels() {
        BufferedImage input = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 2; x++) input.setRGB(x, y, 0xff000000 | (y * 2 + x + 1));
        int[][] expected = {
            {1, 2, 3, 4, 5, 6}, {2, 1, 4, 3, 6, 5}, {6, 5, 4, 3, 2, 1}, {5, 6, 3, 4, 1, 2},
            {1, 3, 5, 2, 4, 6}, {5, 3, 1, 6, 4, 2}, {6, 4, 2, 5, 3, 1}, {2, 4, 6, 1, 3, 5}
        };
        for (int o = 1; o <= 8; o++) {
            var result = OcrTestImage.orient(input, o);
            assertEquals(o >= 5 ? 3 : 2, result.getWidth());
            assertEquals(o >= 5 ? 2 : 3, result.getHeight());
            int i = 0;
            for (int y = 0; y < result.getHeight(); y++)
                for (int x = 0; x < result.getWidth(); x++)
                    assertEquals(expected[o - 1][i++], result.getRGB(x, y) & 255);
        }
    }
}
