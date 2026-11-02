package com.ceos24.autedi.global.storage;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String region;
    private final String customDomain;
    private final FileStorageService fallbackService;

    public S3FileStorageService(
            S3Client s3Client, String bucket, String region, String customDomain, FileStorageService fallbackService) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.region = region;
        this.customDomain = customDomain;
        this.fallbackService = fallbackService;
    }

    public S3FileStorageService(S3Client s3Client, String bucket, String region, String customDomain) {
        this(s3Client, bucket, region, customDomain, null);
    }

    public S3FileStorageService(S3Client s3Client, String bucket, String region) {
        this(s3Client, bucket, region, null, null);
    }

    @Override
    public StoredFile upload(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_FILE);
        }

        String originalFilename =
                StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String extension = getExtension(originalFilename);
        FileType fileType;
        try {
            fileType = FileType.fromExtension(extension);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("파일 바이트 읽기 실패: filename={}, error={}", originalFilename, e.getMessage(), e);
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED, "파일을 읽는 중 오류가 발생했습니다.");
        }

        String fileHash = calculateHash(fileBytes);

        // 이미지 크기 추출 (PDF인 경우 기본 가로/세로 1000 x 1400 설정)
        int width = 1000;
        int height = 1400;
        if (fileType != FileType.PDF) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileBytes));
                if (image != null) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            } catch (Exception e) {
                log.debug("이미지 규격 파싱 실패, 기본 크기로 대체: {}", e.getMessage());
            }
        }

        String storedFilename = UUID.randomUUID() + "." + extension.toLowerCase();
        String cleanSubDir = (subDir != null) ? subDir.replaceAll("^/+", "").replaceAll("/+$", "") : "";
        String s3Key = cleanSubDir.isEmpty() ? storedFilename : cleanSubDir + "/" + storedFilename;

        String contentType = determineContentType(file, extension);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .contentLength((long) fileBytes.length)
                .build();

        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));
            log.info("S3 파일 업로드 성공: bucket={}, key={}", bucket, s3Key);
        } catch (Exception e) {
            log.error("S3 파일 업로드 실패: bucket={}, key={}, error={}", bucket, s3Key, e.getMessage(), e);
            if (fallbackService != null) {
                log.warn("S3 파일 업로드 실패로 LocalStorageService로 자동 폴백합니다: bucket={}, key={}", bucket, s3Key);
                return fallbackService.upload(file, subDir);
            }
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED, "S3 파일 업로드에 실패했습니다: " + e.getMessage());
        }

        String fileUrl = buildFileUrl(s3Key);

        return new StoredFile(
                originalFilename, storedFilename, fileUrl, fileType, (long) fileBytes.length, fileHash, width, height);
    }

    @Override
    public void delete(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            log.warn("삭제 요청된 파일 URL이 비어 있습니다.");
            return;
        }

        if (fileUrl.contains("/files/")) {
            if (fallbackService != null) {
                fallbackService.delete(fileUrl);
                return;
            }
        }

        String key = extractKeyFromUrl(fileUrl);
        if (!StringUtils.hasText(key)) {
            log.warn("S3 키를 추출할 수 없습니다: {}", fileUrl);
            return;
        }

        try {
            DeleteObjectRequest deleteObjectRequest =
                    DeleteObjectRequest.builder().bucket(bucket).key(key).build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("S3 파일 삭제 성공: bucket={}, key={}", bucket, key);
        } catch (Exception e) {
            log.error("S3 파일 삭제 실패: bucket={}, key={}, error={}", bucket, key, e.getMessage(), e);
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED, "S3 파일 삭제에 실패했습니다.");
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        return filename.substring(dotIndex + 1);
    }

    private String calculateHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED, "파일 해시 계산 중 오류가 발생했습니다.");
        }
    }

    private String determineContentType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType;
        }
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "pdf" -> "application/pdf";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private String buildFileUrl(String s3Key) {
        if (StringUtils.hasText(customDomain)) {
            String domain = customDomain.trim().replaceAll("/+$", "");
            if (domain.startsWith("http://") || domain.startsWith("https://")) {
                return domain + "/" + s3Key;
            }
            return "https://" + domain + "/" + s3Key;
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, s3Key);
    }

    private String extractKeyFromUrl(String fileUrl) {
        try {
            String path;
            if (fileUrl.contains("://")) {
                URI uri = URI.create(fileUrl);
                path = uri.getPath();
            } else {
                path = fileUrl;
            }

            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            if (StringUtils.hasText(bucket) && path.startsWith(bucket + "/")) {
                path = path.substring(bucket.length() + 1);
            }

            return URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("URL에서 S3 키 파싱 중 예외 발생: fileUrl={}, error={}", fileUrl, e.getMessage());
            return null;
        }
    }
}
