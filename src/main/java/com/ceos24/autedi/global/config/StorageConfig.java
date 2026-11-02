package com.ceos24.autedi.global.config;

import com.ceos24.autedi.global.storage.FileStorageService;
import com.ceos24.autedi.global.storage.LocalStorageService;
import com.ceos24.autedi.global.storage.S3FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;

@Slf4j
@Configuration
public class StorageConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public FileStorageService s3FileStorageService(
            S3Client s3Client,
            @Value("${aws.s3.bucket:${cloud.aws.s3.bucket:autedi-mvp-develop-bucket}}") String bucket,
            @Value("${aws.region:${cloud.aws.region.static:ap-northeast-2}}") String region,
            @Value("${aws.s3.custom-domain:}") String customDomain,
            @Value("${app.storage.upload-dir:uploads}") String uploadDir,
            @Value("${app.backend-url:${app.frontend-url:http://localhost:8080}}") String backendUrl) {
        log.info("스토리지 빈 활성화: S3FileStorageService (bucket={}, region={})", bucket, region);
        LocalStorageService fallbackService = new LocalStorageService(uploadDir, backendUrl);
        return new S3FileStorageService(s3Client, bucket, region, customDomain, fallbackService);
    }

    @Bean
    @ConditionalOnMissingBean(FileStorageService.class)
    public FileStorageService localStorageService(
            @Value("${app.storage.upload-dir:uploads}") String uploadDir,
            @Value("${app.backend-url:http://localhost:8080}") String backendUrl) {
        log.info("스토리지 빈 활성화: LocalStorageService (폴백 스토리지 사용, uploadDir={})", uploadDir);
        return new LocalStorageService(uploadDir, backendUrl);
    }
}
