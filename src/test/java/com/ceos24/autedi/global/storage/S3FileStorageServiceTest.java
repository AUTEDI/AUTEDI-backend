package com.ceos24.autedi.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private static final String BUCKET = "test-bucket";
    private static final String REGION = "ap-northeast-2";

    private S3FileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new S3FileStorageService(s3Client, BUCKET, REGION);
    }

    @Test
    @DisplayName("이미지 업로드 성공: S3 PutObject 호출 및 메타데이터, SHA-256, 이미지 규격이 올바르게 반환된다")
    void upload_imageSuccess() throws IOException {
        // given: 실제 120x80 크기의 PNG 바이트 생성
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] pngBytes = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile("file", "homework.png", "image/png", pngBytes);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        StoredFile result = storageService.upload(file, "worksheets/42");

        // then
        assertThat(result.originalFilename()).isEqualTo("homework.png");
        assertThat(result.storedFilename()).endsWith(".png");
        assertThat(result.fileType()).isEqualTo(FileType.PNG);
        assertThat(result.fileSize()).isEqualTo((long) pngBytes.length);
        assertThat(result.fileHash()).hasSize(64);
        assertThat(result.width()).isEqualTo(120);
        assertThat(result.height()).isEqualTo(80);
        assertThat(result.fileUrl())
                .startsWith("https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/worksheets/42/")
                .endsWith(".png");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest captured = requestCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo(BUCKET);
        assertThat(captured.key()).startsWith("worksheets/42/").endsWith(".png");
        assertThat(captured.contentType()).isEqualTo("image/png");
        assertThat(captured.contentLength()).isEqualTo((long) pngBytes.length);
    }

    @Test
    @DisplayName("PDF 업로드 성공: 기본 크기(1000x1400) 및 application/pdf Content-Type이 적용된다")
    void upload_pdfSuccess() {
        // given
        byte[] pdfBytes = "%PDF-1.4 dummy pdf content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "exam.pdf", "application/pdf", pdfBytes);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        StoredFile result = storageService.upload(file, "worksheets/10");

        // then
        assertThat(result.fileType()).isEqualTo(FileType.PDF);
        assertThat(result.width()).isEqualTo(1000);
        assertThat(result.height()).isEqualTo(1400);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("커스텀 도메인 설정 시 생성된 URL에 커스텀 도메인이 적용된다")
    void upload_withCustomDomain() {
        // given
        S3FileStorageService customDomainService =
                new S3FileStorageService(s3Client, BUCKET, REGION, "https://cdn.autedi.com");
        byte[] dummyBytes = "test content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "image.jpg", "image/jpeg", dummyBytes);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        StoredFile result = customDomainService.upload(file, "worksheets/1");

        // then
        assertThat(result.fileUrl()).startsWith("https://cdn.autedi.com/worksheets/1/");
    }

    @Test
    @DisplayName("빈 파일 업로드 시 EMPTY_FILE 예외가 발생하고 S3 호출이 발생하지 않는다")
    void upload_emptyFile_throwsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storageService.upload(emptyFile, "worksheets/1"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPTY_FILE);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("지원하지 않는 확장자 업로드 시 UNSUPPORTED_FILE_TYPE 예외가 발생한다")
    void upload_unsupportedFileType_throwsUnsupportedFileType() {
        MockMultipartFile invalidFile = new MockMultipartFile("file", "script.sh", "text/plain", "echo hi".getBytes());

        assertThatThrownBy(() -> storageService.upload(invalidFile, "worksheets/1"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_FILE_TYPE);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("S3 putObject 실패 시 fallbackService가 없으면 FILE_STORAGE_FAILED 예외로 전환된다")
    void upload_s3Exception_throwsFileStorageFailed() {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "sample".getBytes());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow((S3Exception)
                        S3Exception.builder().message("S3 connection failed").build());

        assertThatThrownBy(() -> storageService.upload(file, "worksheets/1"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_STORAGE_FAILED);
    }

    @Test
    @DisplayName("S3 putObject 실패 시 fallbackService가 존재하면 로컬 스토리지로 자동 폴백한다")
    void upload_s3Exception_withFallbackService_fallbacksToLocalStorage() {
        FileStorageService mockFallback = org.mockito.Mockito.mock(FileStorageService.class);
        S3FileStorageService serviceWithFallback =
                new S3FileStorageService(s3Client, BUCKET, REGION, null, mockFallback);

        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "sample".getBytes());
        StoredFile expectedFallbackFile = new StoredFile(
                "image.png", "uuid.png", "http://localhost:8080/files/uuid.png", FileType.PNG, 6L, "hash", 100, 100);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow((S3Exception)
                        S3Exception.builder().message("S3 Access Denied").build());
        when(mockFallback.upload(file, "worksheets/1")).thenReturn(expectedFallbackFile);

        StoredFile result = serviceWithFallback.upload(file, "worksheets/1");

        assertThat(result).isEqualTo(expectedFallbackFile);
        verify(mockFallback).upload(file, "worksheets/1");
    }

    @Test
    @DisplayName("로컬 폴백 URL 삭제 요청 시 fallbackService.delete가 호출된다")
    void delete_localFallbackUrl_callsFallbackDelete() {
        FileStorageService mockFallback = org.mockito.Mockito.mock(FileStorageService.class);
        S3FileStorageService serviceWithFallback =
                new S3FileStorageService(s3Client, BUCKET, REGION, null, mockFallback);

        String localUrl = "http://localhost:8080/files/worksheets/1/uuid.png";
        serviceWithFallback.delete(localUrl);

        verify(mockFallback).delete(localUrl);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("파일 삭제 성공: S3 URL에서 키가 올바르게 추출되어 DeleteObjectRequest가 호출된다")
    void delete_virtualHostedUrl_success() {
        String fileUrl = "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/worksheets/10/abcd-1234.png";

        storageService.delete(fileUrl);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        DeleteObjectRequest captured = captor.getValue();
        assertThat(captured.bucket()).isEqualTo(BUCKET);
        assertThat(captured.key()).isEqualTo("worksheets/10/abcd-1234.png");
    }

    @Test
    @DisplayName("경로 스타일 S3 URL 파일 삭제 시 버킷 접두사가 안전하게 제거되고 키만 추출된다")
    void delete_pathStyleUrl_success() {
        String fileUrl = "https://s3." + REGION + ".amazonaws.com/" + BUCKET + "/worksheets/20/sample.jpg";

        storageService.delete(fileUrl);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("worksheets/20/sample.jpg");
    }

    @Test
    @DisplayName("URL이 null이거나 빈 문자열인 경우 예외 없이 안전하게 무시된다")
    void delete_blankUrl_safelyIgnored() {
        storageService.delete(null);
        storageService.delete("   ");

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("S3 deleteObject 실패 시 FILE_STORAGE_FAILED 예외가 발생한다")
    void delete_s3Exception_throwsFileStorageFailed() {
        String fileUrl = "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/worksheets/1/test.png";

        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow((S3Exception)
                S3Exception.builder().message("Access denied").build());

        assertThatThrownBy(() -> storageService.delete(fileUrl))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_STORAGE_FAILED);
    }
}
