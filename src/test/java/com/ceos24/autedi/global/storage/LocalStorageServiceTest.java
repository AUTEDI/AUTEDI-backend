package com.ceos24.autedi.global.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class LocalStorageServiceTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "jpeg", "JPG", "PNG"})
    void preservesOriginalBytesAndStoresSeparateUprightPng(String extension) throws Exception {
        boolean jpeg = !extension.equals("PNG");
        byte[] source = TestWorksheetImages.withOrientation(jpeg, 6, ByteOrder.LITTLE_ENDIAN);
        LocalStorageService storage = storage();
        StoredFile stored = storage.upload(
                new MockMultipartFile("file", "원본." + extension, "application/octet-stream", source), "worksheets/7");
        assertArrayEquals(source, storage.load(stored.fileUrl()).getContentAsByteArray());
        assertEquals(LocalStorageService.sha256(source), stored.fileHash());
        assertEquals((long) source.length, stored.fileSize().longValue());
        assertEquals(3, stored.width());
        assertEquals(2, stored.height());
        assertNotEquals(stored.fileUrl(), stored.preparedImageUrl());
        byte[] png = storage.load(stored.preparedImageUrl()).getContentAsByteArray();
        assertTrue(WorksheetImagePreparer.isPng(png));
        assertEquals(1, WorksheetImagePreparer.orientation(png, false));
        StoredFile next =
                storage.upload(new MockMultipartFile("file", "원본." + extension, null, source), "worksheets/7");
        assertNotEquals(stored.fileUrl(), next.fileUrl());
        assertArrayEquals(source, storage.load(stored.fileUrl()).getContentAsByteArray());
        assertArrayEquals(png, storage.load(stored.preparedImageUrl()).getContentAsByteArray());
    }

    @Test
    void rejectsPdfUnreadableBytesFormatMismatchAndInvalidExifWithoutCreatingFiles() throws Exception {
        LocalStorageService storage = storage();
        assertError(
                ErrorCode.UNSUPPORTED_FILE_TYPE,
                () -> storage.upload(
                        new MockMultipartFile("file", "paper.pdf", "application/pdf", "%PDF".getBytes()),
                        "worksheets/7"));
        assertError(
                ErrorCode.INVALID_IMAGE,
                () -> storage.upload(
                        new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()),
                        "worksheets/7"));
        byte[] png = TestWorksheetImages.png(2, 3);
        assertError(
                ErrorCode.INVALID_IMAGE,
                () -> storage.upload(new MockMultipartFile("file", "wrong.jpg", "image/jpeg", png), "worksheets/7"));
        byte[] invalidExif = TestWorksheetImages.withOrientation(true, 9, ByteOrder.BIG_ENDIAN);
        assertError(
                ErrorCode.INVALID_IMAGE,
                () -> storage.upload(new MockMultipartFile("file", "broken.jpg", null, invalidExif), "worksheets/7"));
        try (var paths = Files.walk(temporary)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void rejectsEmptyOversizeAndHugeDecodedDimensions() throws Exception {
        LocalStorageService storage = storage();
        assertError(
                ErrorCode.EMPTY_FILE,
                () -> storage.upload(new MockMultipartFile("file", "empty.png", null, new byte[0]), "worksheets/7"));
        assertError(
                ErrorCode.FILE_TOO_LARGE,
                () -> storage.upload(
                        new MockMultipartFile("file", "big.png", null, new byte[WorksheetImagePreparer.MAX_BYTES + 1]),
                        "worksheets/7"));
        byte[] enormous = TestWorksheetImages.oversizedHeader();
        assertError(
                ErrorCode.FILE_TOO_LARGE,
                () -> storage.upload(new MockMultipartFile("file", "huge.png", null, enormous), "worksheets/7"));
    }

    @Test
    void cannotLoadOrWriteOutsideStorageAndDeletionOnlyTargetsRequestedFile() throws Exception {
        LocalStorageService storage = storage();
        byte[] png = TestWorksheetImages.png(2, 3);
        Path outside = temporary.resolve("outside.png");
        Files.write(outside, png);
        assertError(ErrorCode.FILE_NOT_FOUND, () -> storage.load("http://backend.test/files/../outside.png"));
        assertError(ErrorCode.FILE_NOT_FOUND, () -> storage.load("https://other.test/files/outside.png"));
        assertError(
                ErrorCode.INVALID_INPUT_VALUE,
                () -> storage.upload(new MockMultipartFile("file", "test.png", null, png), "../escape"));
        StoredFile stored = storage.upload(new MockMultipartFile("file", "test.png", null, png), "worksheets/7");
        storage.delete(stored.preparedImageUrl());
        assertError(ErrorCode.FILE_NOT_FOUND, () -> storage.load(stored.preparedImageUrl()));
        assertArrayEquals(png, storage.load(stored.fileUrl()).getContentAsByteArray());
        assertArrayEquals(png, Files.readAllBytes(outside));
    }

    private LocalStorageService storage() {
        return new LocalStorageService(temporary.resolve("uploads").toString(), "http://backend.test");
    }

    private static void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable executable) {
        assertEquals(code, assertThrows(CustomException.class, executable).getErrorCode());
    }
}
