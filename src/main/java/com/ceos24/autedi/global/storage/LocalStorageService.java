package com.ceos24.autedi.global.storage;

import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class LocalStorageService implements FileStorageService {

    private final Path rootLocation;
    private final String baseUrl;

    public LocalStorageService(
            @Value("${app.storage.upload-dir:uploads}") String uploadDir,
            @Value("${app.backend-url:http://localhost:8080}") String backendUrl
    ) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = backendUrl.endsWith("/") ? backendUrl : backendUrl + "/";
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED, "업로드 루트 디렉터리를 생성할 수 없습니다.");
        }
    }

    @Override
    public StoredFile upload(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_FILE);
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String extension = getExtension(originalFilename);
        FileType fileType;
        try {
            fileType = FileType.fromExtension(extension);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String storedFilename = UUID.randomUUID() + "." + extension.toLowerCase();
        Path targetDir = rootLocation.resolve(subDir != null ? subDir : "").normalize();

        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(storedFilename);

            // 파일 저장
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 파일 해시 계산
            String fileHash = calculateHash(targetPath);

            // 이미지 크기 추출 (PDF인 경우 기본 가로/세로 1000 x 1400 설정)
            int width = 1000;
            int height = 1400;
            if (fileType != FileType.PDF) {
                try {
                    BufferedImage image = ImageIO.read(targetPath.toFile());
                    if (image != null) {
                        width = image.getWidth();
                        height = image.getHeight();
                    }
                } catch (Exception e) {
                    log.debug("이미지 규격 파싱 실패, 기본 크기로 대체: {}", e.getMessage());
                }
            }

            String relativePath = (subDir != null && !subDir.isEmpty() ? subDir + "/" : "") + storedFilename;
            String fileUrl = baseUrl + "files/" + relativePath;

            return new StoredFile(
                    originalFilename,
                    storedFilename,
                    fileUrl,
                    fileType,
                    file.getSize(),
                    fileHash,
                    width,
                    height
            );

        } catch (IOException e) {
            log.error("파일 저장 실패: filename={}, error={}", originalFilename, e.getMessage(), e);
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    @Override
    public void delete(String fileUrl) {
        // 로컬 파일 삭제 (선택적 구현)
        log.info("파일 삭제 요청: {}", fileUrl);
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        return filename.substring(dotIndex + 1);
    }

    private String calculateHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(fileBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED, "파일 해시 계산 중 오류가 발생했습니다.");
        }
    }
}
