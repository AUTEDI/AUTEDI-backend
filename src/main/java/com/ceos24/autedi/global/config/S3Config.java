package com.ceos24.autedi.global.config;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Slf4j
@Configuration
public class S3Config {

    @Value("${aws.region:${cloud.aws.region.static:ap-northeast-2}}")
    private String region;

    @Value("${aws.credentials.access-key:${cloud.aws.credentials.access-key:}}")
    private String accessKey;

    @Value("${aws.credentials.secret-key:${cloud.aws.credentials.secret-key:}}")
    private String secretKey;

    @Value("${aws.s3.endpoint:}")
    private String endpoint;

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public S3Client s3Client() {
        log.info("[startup] S3 클라이언트 초기화: region={}", region);
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));

        String cleanAccessKey =
                accessKey != null ? accessKey.trim().replace("\"", "").replace("'", "") : "";
        String cleanSecretKey =
                secretKey != null ? secretKey.trim().replace("\"", "").replace("'", "") : "";

        if (StringUtils.hasText(cleanAccessKey) && StringUtils.hasText(cleanSecretKey)) {
            log.info("[startup] S3 자격 증명 방식: provider=static");
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(cleanAccessKey, cleanSecretKey)));
        } else {
            log.info("[startup] S3 자격 증명 방식: provider=default");
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        if (StringUtils.hasText(endpoint)) {
            log.info("[startup] S3 커스텀 엔드포인트 적용: endpoint={}", endpoint);
            builder.endpointOverride(URI.create(endpoint.trim())).forcePathStyle(true);
        }

        return builder.build();
    }
}
