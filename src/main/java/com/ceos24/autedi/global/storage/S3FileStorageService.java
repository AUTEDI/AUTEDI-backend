package com.ceos24.autedi.global.storage;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3와 로컬 모두 같은 이미지 준비 계약을 사용한다. 원본과 보정 PNG는 별도 객체로 보존한다. */
@Slf4j
public class S3FileStorageService implements FileStorageService {
    private final S3Client s3Client;
    private final String bucket;
    private final String region;
    private final String customDomain;
    private final FileStorageService fallbackService;
    private final WorksheetImagePreparer preparer = new WorksheetImagePreparer();

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
        if (file == null || file.isEmpty()) throw new CustomException(ErrorCode.EMPTY_FILE);
        if (file.getSize() > WorksheetImagePreparer.MAX_BYTES) throw new CustomException(ErrorCode.FILE_TOO_LARGE);
        String name = file.getOriginalFilename();
        name = StringUtils.getFilename(name == null ? "file" : name.replace('\\', '/'));
        int dot = name.lastIndexOf('.');
        FileType type;
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        try {
            type = FileType.fromExtension(extension);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (type == FileType.PDF) throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        byte[] original;
        PreparedImage prepared;
        try (var input = file.getInputStream()) {
            original = input.readNBytes(WorksheetImagePreparer.MAX_BYTES + 1);
            if (original.length > WorksheetImagePreparer.MAX_BYTES) throw new CustomException(ErrorCode.FILE_TOO_LARGE);
            prepared = preparer.prepare(original, type);
        } catch (WorksheetImagePreparer.ImageLimitException e) {
            throw new CustomException(ErrorCode.FILE_TOO_LARGE, e.getMessage());
        } catch (IOException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_IMAGE);
        }
        String directory = subDir == null ? "" : subDir.replaceAll("^/+|/+$", "");
        validateKey(directory.isEmpty() ? "image" : directory);
        String id = UUID.randomUUID().toString();
        String filename = id + "." + extension;
        String prefix = directory.isEmpty() ? "" : directory + "/";
        String originalKey = prefix + filename;
        String preparedKey = prefix + id + "-prepared.png";
        try {
            put(originalKey, original, type == FileType.PNG ? "image/png" : "image/jpeg");
            put(preparedKey, prepared.png(), "image/png");
        } catch (RuntimeException e) {
            // 이번 요청이 선택한 UUID 객체만 정리한다. 이전 업로드나 다른 키는 삭제하지 않는다.
            cleanup(originalKey);
            cleanup(preparedKey);
            if (fallbackService != null) {
                log.warn("S3 업로드 실패로 로컬 저장소를 사용합니다.");
                return fallbackService.upload(file, subDir);
            }
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED);
        }
        return new StoredFile(
                name,
                filename,
                buildFileUrl(originalKey),
                type,
                (long) original.length,
                LocalStorageService.sha256(original),
                buildFileUrl(preparedKey),
                prepared.width(),
                prepared.height());
    }

    private void put(String key, byte[] bytes, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) bytes.length)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public Resource load(String fileUrl) {
        if (isLocal(fileUrl)) return fallbackService.load(fileUrl);
        String key = extractKeyFromUrl(fileUrl);
        try (var input = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            byte[] bytes = input.readNBytes(WorksheetImagePreparer.MAX_BYTES + 1);
            if (bytes.length > WorksheetImagePreparer.MAX_BYTES) throw new CustomException(ErrorCode.FILE_TOO_LARGE);
            return new ByteArrayResource(bytes);
        } catch (NoSuchKeyException e) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        } catch (CustomException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Override
    public void delete(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) return;
        if (isLocal(fileUrl)) {
            fallbackService.delete(fileUrl);
            return;
        }
        String key = extractKeyFromUrl(fileUrl);
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    private boolean isLocal(String url) {
        return fallbackService != null && url != null && url.contains("/files/");
    }

    private void cleanup(String key) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException ignored) {
            log.warn("실패한 S3 업로드의 임시 객체 정리를 완료하지 못했습니다.");
        }
    }

    private String buildFileUrl(String key) {
        return baseUrl() + "/" + key;
    }

    private String baseUrl() {
        if (!StringUtils.hasText(customDomain)) return bucketUrl();
        String domain = customDomain.trim().replaceAll("/+$", "");
        return domain.contains("://") ? domain : "https://" + domain;
    }

    private String bucketUrl() {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com";
    }

    private String extractKeyFromUrl(String fileUrl) {
        try {
            if (!StringUtils.hasText(fileUrl)) throw new IllegalArgumentException();
            String path;
            if (fileUrl.startsWith(baseUrl() + "/")) {
                path = fileUrl.substring(baseUrl().length() + 1);
            } else if (fileUrl.startsWith(bucketUrl() + "/")) {
                path = fileUrl.substring(bucketUrl().length() + 1);
            } else {
                URI uri = URI.create(fileUrl);
                if (!"https".equals(uri.getScheme())
                        || !uri.getHost().equals("s3." + region + ".amazonaws.com")
                        || !uri.getPath().startsWith("/" + bucket + "/")) throw new IllegalArgumentException();
                path = uri.getRawPath().substring(bucket.length() + 2);
            }
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
            validateKey(path);
            return path;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    private static void validateKey(String key) {
        for (String segment : key.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.contains("\\"))
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
