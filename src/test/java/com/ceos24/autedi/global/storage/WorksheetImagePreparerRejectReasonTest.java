package com.ceos24.autedi.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 이미지 검사 거부가 올바른 사유 코드와 기존 문구로 던져지는지 확인한다. */
class WorksheetImagePreparerRejectReasonTest {

    private final WorksheetImagePreparer preparer = new WorksheetImagePreparer();

    @Test
    @DisplayName("빈 이미지는 EMPTY_IMAGE이고 문구는 그대로다")
    void emptyImage() {
        assertThatThrownBy(() -> preparer.prepare(new byte[0], FileType.JPG))
                .isInstanceOf(ImageRejectedException.class)
                .hasMessage("빈 이미지입니다.")
                .extracting(e -> ((ImageRejectedException) e).getReason())
                .isEqualTo(ImageRejectReason.EMPTY_IMAGE);
    }

    @Test
    @DisplayName("확장자와 실제 형식이 다르면 FORMAT_MISMATCH이고 문구는 그대로다")
    void formatMismatch() throws Exception {
        byte[] png = TestWorksheetImages.png(2, 3);

        assertThatThrownBy(() -> preparer.prepare(png, FileType.JPG))
                .isInstanceOf(ImageRejectedException.class)
                .hasMessage("확장자와 실제 JPG/JPEG·PNG 형식이 일치해야 합니다.")
                .extracting(e -> ((ImageRejectedException) e).getReason())
                .isEqualTo(ImageRejectReason.FORMAT_MISMATCH);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corruptExifCases")
    @DisplayName("손상된 EXIF는 위치에 맞는 사유 코드로 거부된다")
    void corruptExif(String name, byte[] tiff, ImageRejectReason expected) {
        byte[] jpeg = jpegWithExif(tiff);

        assertThatThrownBy(() -> WorksheetImagePreparer.orientation(jpeg, true))
                .isInstanceOf(ImageRejectedException.class)
                .extracting(e -> ((ImageRejectedException) e).getReason())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("범위 밖 방향 값을 직접 넘기면 ORIENTATION_INVALID이다")
    void invalidOrientationValue() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        assertThatThrownBy(() -> WorksheetImagePreparer.orient(image, 9))
                .isInstanceOf(ImageRejectedException.class)
                .hasMessage("잘못된 방향입니다.")
                .extracting(e -> ((ImageRejectedException) e).getReason())
                .isEqualTo(ImageRejectReason.ORIENTATION_INVALID);
    }

    @Test
    @DisplayName("정상 EXIF 방향은 거부되지 않는다")
    void validExifIsNotRejected() throws Exception {
        byte[] jpeg = TestWorksheetImages.withOrientation(true, 6, ByteOrder.LITTLE_ENDIAN);

        assertThat(WorksheetImagePreparer.orientation(jpeg, true)).isEqualTo(6);
    }

    static Stream<Arguments> corruptExifCases() {
        return Stream.of(
                Arguments.of("EXIF 헤더가 8바이트보다 짧음", new byte[4], ImageRejectReason.EXIF_HEADER_CORRUPT),
                Arguments.of(
                        "바이트 순서 표시가 II/MM이 아님",
                        new byte[] {'X', 'X', 0, 0, 0, 0, 0, 0},
                        ImageRejectReason.EXIF_BYTE_ORDER_UNSUPPORTED),
                Arguments.of(
                        "TIFF 매직 넘버가 42가 아님",
                        littleEndian(8)
                                .put((byte) 'I')
                                .put((byte) 'I')
                                .putShort((short) 43)
                                .putInt(8)
                                .array(),
                        ImageRejectReason.TIFF_HEADER_CORRUPT),
                Arguments.of(
                        "IFD 위치가 데이터 밖임",
                        littleEndian(8)
                                .put((byte) 'I')
                                .put((byte) 'I')
                                .putShort((short) 42)
                                .putInt(100)
                                .array(),
                        ImageRejectReason.EXIF_OFFSET_CORRUPT),
                Arguments.of(
                        "항목 수는 있는데 항목이 잘림",
                        littleEndian(10)
                                .put((byte) 'I')
                                .put((byte) 'I')
                                .putShort((short) 42)
                                .putInt(8)
                                .putShort((short) 1)
                                .array(),
                        ImageRejectReason.EXIF_ENTRY_CORRUPT),
                Arguments.of(
                        "방향 항목의 형식이 SHORT가 아님",
                        orientationEntry(4, (short) 1),
                        ImageRejectReason.EXIF_ORIENTATION_FORMAT_UNSUPPORTED),
                Arguments.of(
                        "방향 값이 1~8 밖임", orientationEntry(3, (short) 9), ImageRejectReason.EXIF_ORIENTATION_INVALID));
    }

    private static ByteBuffer littleEndian(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** IFD 항목 1개(방향 태그 274)만 있는 리틀엔디언 TIFF를 만든다. */
    private static byte[] orientationEntry(int type, short value) {
        return littleEndian(22)
                .put((byte) 'I')
                .put((byte) 'I')
                .putShort((short) 42)
                .putInt(8)
                .putShort((short) 1)
                .putShort((short) 274)
                .putShort((short) type)
                .putInt(1)
                .putShort(value)
                .putShort((short) 0)
                .array();
    }

    /** SOI + Exif APP1 세그먼트(주어진 TIFF 바이트)만 있는 최소 JPEG 바이트. 방향 읽기만 검사하므로 이미지 본문은 없다. */
    private static byte[] jpegWithExif(byte[] tiff) {
        ByteBuffer out = ByteBuffer.allocate(12 + tiff.length);
        out.put((byte) 0xFF).put((byte) 0xD8);
        out.put((byte) 0xFF).put((byte) 0xE1);
        out.putShort((short) (8 + tiff.length));
        out.put(new byte[] {'E', 'x', 'i', 'f', 0, 0});
        out.put(tiff);
        return out.array();
    }
}
