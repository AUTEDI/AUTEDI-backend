package com.ceos24.autedi.global.storage;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class LocalStorageService implements FileStorageService {

    private final Path rootLocation;
    private final String filePrefix;
    private final WorksheetImagePreparer imagePreparer = new WorksheetImagePreparer();

    public LocalStorageService(
            @Value("${app.storage.upload-dir:uploads}") String uploadDir,
            @Value("${app.backend-url:http://localhost:8080}") String backendUrl) {
        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(root);
            this.rootLocation = root.toRealPath();
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED, "업로드 루트 디렉터리를 생성할 수 없습니다.");
        }
        this.filePrefix = (backendUrl.endsWith("/") ? backendUrl : backendUrl + "/") + "files/";
    }

    @Override
    public StoredFile upload(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) throw new CustomException(ErrorCode.EMPTY_FILE);
        if (file.getSize() > WorksheetImagePreparer.MAX_BYTES) throw new CustomException(ErrorCode.FILE_TOO_LARGE);
        String suppliedName = file.getOriginalFilename();
        String originalFilename =
                StringUtils.getFilename(suppliedName == null ? "file" : suppliedName.replace('\\', '/'));
        String extension = getExtension(originalFilename).toLowerCase(Locale.ROOT);
        FileType fileType;
        try {
            fileType = FileType.fromExtension(extension);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (fileType == FileType.PDF) throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        byte[] original;
        PreparedImage prepared;
        try (var input = file.getInputStream()) {
            original = input.readNBytes(WorksheetImagePreparer.MAX_BYTES + 1);
            if (original.length > WorksheetImagePreparer.MAX_BYTES) throw new CustomException(ErrorCode.FILE_TOO_LARGE);
            prepared = imagePreparer.prepare(original, fileType);
        } catch (WorksheetImagePreparer.ImageLimitException e) {
            throw new CustomException(ErrorCode.FILE_TOO_LARGE, e.getMessage());
        } catch (IllegalArgumentException | IOException e) {
            throw new CustomException(ErrorCode.INVALID_IMAGE, e.getMessage());
        }

        Path targetDir = rootLocation.resolve(subDir == null ? "" : subDir).normalize();
        if (!targetDir.startsWith(rootLocation)) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        String id = UUID.randomUUID().toString();
        String storedFilename = id + "." + extension;
        String preparedFilename = id + "-prepared.png";
        Path originalPath = null, preparedPath = null;
        boolean originalCreated = false, preparedCreated = false;
        try {
            Files.createDirectories(targetDir);
            targetDir = targetDir.toRealPath();
            if (!targetDir.startsWith(rootLocation)) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            originalPath = targetDir.resolve(storedFilename);
            preparedPath = targetDir.resolve(preparedFilename);
            // 이번 UUID로 새로 만든 파일만 기록하며 기존 원본을 덮어쓰지 않는다.
            Files.createFile(originalPath);
            originalCreated = true;
            Files.write(originalPath, original);
            Files.createFile(preparedPath);
            preparedCreated = true;
            Files.write(preparedPath, prepared.png());
            String relativeDir = rootLocation.relativize(targetDir).toString().replace('\\', '/');
            String prefix = filePrefix + (relativeDir.isEmpty() ? "" : relativeDir + "/");
            return new StoredFile(
                    originalFilename,
                    storedFilename,
                    prefix + storedFilename,
                    fileType,
                    (long) original.length,
                    sha256(original),
                    prefix + preparedFilename,
                    prepared.width(),
                    prepared.height());
        } catch (IOException e) {
            if (preparedCreated) removeNewFile(preparedPath);
            if (originalCreated) removeNewFile(originalPath);
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Override
    public Resource load(String fileUrl) {
        try {
            Path path = resolve(fileUrl).toRealPath();
            if (!path.startsWith(rootLocation) || !Files.isRegularFile(path))
                throw new CustomException(ErrorCode.FILE_NOT_FOUND);
            return new FileSystemResource(path);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    @Override
    public void delete(String fileUrl) {
        Path path = resolve(fileUrl);
        if (!Files.exists(path)) return;
        try {
            if (!path.toRealPath().startsWith(rootLocation)) throw new CustomException(ErrorCode.FILE_NOT_FOUND);
            Files.delete(path);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        return filename.substring(dotIndex + 1);
    }

    private Path resolve(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(filePrefix)) throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        Path path = rootLocation.resolve(fileUrl.substring(filePrefix.length())).normalize();
        if (!path.startsWith(rootLocation)) throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        return path;
    }

    private void removeNewFile(Path path) {
        if (path == null || !path.startsWith(rootLocation)) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("실패한 업로드의 파일 정리를 완료하지 못했습니다.");
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
