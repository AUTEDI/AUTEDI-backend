package com.ceos24.autedi.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** 업로드·조회·삭제 실패의 로그 형식과 원인(cause) 보존을 확인한다. */
@ExtendWith(MockitoExtension.class)
class S3FileStorageServiceLogTest {

    private static final String BUCKET = "test-bucket";
    private static final String REGION = "ap-northeast-2";
    private static final String SECRET_NAME = "secret-student-name.jpg";

    @Mock
    private S3Client s3Client;

    private S3FileStorageService storageService;
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        storageService = new S3FileStorageService(s3Client, BUCKET, REGION);
        logger = (Logger) LoggerFactory.getLogger(S3FileStorageService.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    @DisplayName("이미지 검사 거부 로그: 사유 코드와 ext/sizeKB만 남고 파일명·스택트레이스는 없다. 응답 문구는 그대로다")
    void rejectionLogHasReasonCodeOnly() throws Exception {
        byte[] png = TestWorksheetImages.png(2, 3);
        MockMultipartFile mismatched = new MockMultipartFile("file", SECRET_NAME, "image/jpeg", png);

        assertThatThrownBy(() -> storageService.upload(mismatched, "worksheets/1"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE)
                .hasMessageContaining("확장자와 실제 JPG/JPEG·PNG 형식이 일치해야 합니다");

        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo("WARN");
        assertThat(event.getFormattedMessage())
                .isEqualTo("[upload] 이미지 검사 거부: ext=jpg, sizeKB=" + png.length / 1024 + ", reason=FORMAT_MISMATCH");
        assertThat(event.getFormattedMessage()).doesNotContain("secret-student-name");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("이미지를 읽다가 IOException이 나면 reason=READ_FAILED로 남는다")
    void readFailureLogsReadFailed() throws Exception {
        MultipartFile unreadable = mock(MultipartFile.class);
        when(unreadable.getOriginalFilename()).thenReturn(SECRET_NAME);
        when(unreadable.isEmpty()).thenReturn(false);
        when(unreadable.getSize()).thenReturn(2048L);
        when(unreadable.getInputStream()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> storageService.upload(unreadable, "worksheets/1"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE);

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getFormattedMessage())
                .isEqualTo("[upload] 이미지 검사 거부: ext=jpg, sizeKB=2, reason=READ_FAILED");
        assertThat(logs.list.get(0).getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("사유 코드가 없는 IllegalArgumentException은 reason=UNCLASSIFIED로 남고 문구는 응답에 간다")
    void unclassifiedRejection() throws Exception {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn(SECRET_NAME);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getSize()).thenReturn(1024L);
        when(broken.getInputStream()).thenThrow(new IllegalArgumentException("jdk said no"));

        assertThatThrownBy(() -> storageService.upload(broken, "worksheets/1"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE)
                .hasMessage("jdk said no");

        assertThat(logs.list.get(0).getFormattedMessage())
                .isEqualTo("[upload] 이미지 검사 거부: ext=jpg, sizeKB=1, reason=UNCLASSIFIED");
        assertThat(logs.list.get(0).getFormattedMessage()).doesNotContain("jdk said no");
    }

    @Test
    @DisplayName("S3 업로드 실패에 폴백이 없으면 FILE_STORAGE_FAILED에 원인(S3 예외)이 담긴다")
    void uploadFailureKeepsCause() throws Exception {
        S3Exception s3Failure =
                (S3Exception) S3Exception.builder().message("Access Denied").build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(s3Failure);
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", TestWorksheetImages.png(2, 3));

        assertThatThrownBy(() -> storageService.upload(file, "worksheets/1"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_STORAGE_FAILED)
                .hasCause(s3Failure);
    }

    @Test
    @DisplayName("S3 업로드 실패 후 로컬 폴백을 쓰면 WARN 한 줄에 fallback=local, errorType이 남고 스택트레이스는 없다")
    void fallbackLogsErrorType() throws Exception {
        FileStorageService fallback = mock(FileStorageService.class);
        S3FileStorageService withFallback = new S3FileStorageService(s3Client, BUCKET, REGION, null, fallback);
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", TestWorksheetImages.png(2, 3));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow((S3Exception)
                        S3Exception.builder().message("Access Denied").build());
        when(fallback.upload(file, "worksheets/1"))
                .thenReturn(new StoredFile(
                        "image.png", "id.png", "http://localhost/files/id.png", FileType.PNG, 6L, "hash", 2, 3));

        withFallback.upload(file, "worksheets/1");

        ILoggingEvent event = logs.list.stream()
                .filter(e -> e.getFormattedMessage().contains("로컬 저장소로 대체"))
                .findFirst()
                .orElseThrow();
        assertThat(event.getLevel().toString()).isEqualTo("WARN");
        assertThat(event.getFormattedMessage())
                .isEqualTo("[upload] S3 저장 실패, 로컬 저장소로 대체: fallback=local, errorType=S3Exception");
        assertThat(event.getFormattedMessage()).doesNotContain("Access Denied");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("S3 조회 실패 시 FILE_STORAGE_FAILED에 원인이 담긴다")
    void loadFailureKeepsCause() {
        S3Exception s3Failure =
                (S3Exception) S3Exception.builder().message("timeout").build();
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(s3Failure);

        assertThatThrownBy(() -> storageService.load(
                        "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/worksheets/1/test.png"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_STORAGE_FAILED)
                .hasCause(s3Failure);
    }

    @Test
    @DisplayName("S3 삭제 실패 시 FILE_STORAGE_FAILED에 원인이 담긴다")
    void deleteFailureKeepsCause() {
        S3Exception s3Failure =
                (S3Exception) S3Exception.builder().message("denied").build();
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(s3Failure);

        assertThatThrownBy(() -> storageService.delete(
                        "https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/worksheets/1/test.png"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_STORAGE_FAILED)
                .hasCause(s3Failure);
    }
}
