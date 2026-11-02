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
        log.info("AWS S3Client 초기화: region={}", region);
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));

        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            log.info("AWS S3Client: StaticCredentialsProvider (AccessKey 주입) 사용");
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())));
        } else {
            log.info("AWS S3Client: DefaultCredentialsProvider (IAM Role / 환경변수) 사용");
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        if (StringUtils.hasText(endpoint)) {
            log.info("AWS S3Client: Custom Endpoint 적용 ({})", endpoint);
            builder.endpointOverride(URI.create(endpoint.trim())).forcePathStyle(true);
        }

        return builder.build();
    }
}
