package com.ceos24.autedi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceos24.autedi.global.storage.FileStorageService;
import com.ceos24.autedi.global.storage.LocalStorageService;
import com.ceos24.autedi.global.storage.S3FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

class StorageConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(StorageConfig.class, S3Config.class);

    @Test
    @DisplayName("app.storage.type=local 인 경우 LocalStorageService 빈이 등록되고 S3Client는 등록되지 않는다")
    void whenStorageTypeIsLocal_thenLocalStorageServiceIsCreated() {
        contextRunner.withPropertyValues("app.storage.type=local").run(context -> {
            assertThat(context).hasSingleBean(FileStorageService.class);
            assertThat(context.getBean(FileStorageService.class)).isInstanceOf(LocalStorageService.class);
            assertThat(context).doesNotHaveBean(S3Client.class);
        });
    }

    @Test
    @DisplayName("app.storage.type 프로퍼티가 누락된 경우 기본 폴백으로 LocalStorageService 빈이 등록된다")
    void whenStorageTypeIsMissing_thenLocalStorageServiceIsCreatedAsFallback() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FileStorageService.class);
            assertThat(context.getBean(FileStorageService.class)).isInstanceOf(LocalStorageService.class);
            assertThat(context).doesNotHaveBean(S3Client.class);
        });
    }

    @Test
    @DisplayName("app.storage.type=s3 및 AWS 설정이 제공된 경우 S3FileStorageService 및 S3Client 빈이 등록된다")
    void whenStorageTypeIsS3_thenS3FileStorageServiceAndS3ClientAreCreated() {
        contextRunner
                .withPropertyValues(
                        "app.storage.type=s3",
                        "aws.region=ap-northeast-2",
                        "aws.s3.bucket=test-bucket",
                        "aws.credentials.access-key=dummy-key",
                        "aws.credentials.secret-key=dummy-secret")
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageService.class);
                    assertThat(context.getBean(FileStorageService.class)).isInstanceOf(S3FileStorageService.class);
                    assertThat(context).hasSingleBean(S3Client.class);
                });
    }
}
