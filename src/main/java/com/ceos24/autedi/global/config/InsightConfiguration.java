package com.ceos24.autedi.global.config;

import com.ceos24.autedi.domain.insight.client.*;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InsightClientProperties.class)
public class InsightConfiguration {
    @Bean
    public InsightClient insightClient(
            @Qualifier("terraHttpClient") HttpClient http,
            InsightClientProperties properties,
            @Value("${app.ai.insight.endpoint:${app.ai.terra.endpoint:https://api.openai.com/v1/responses}}")
                    URI endpoint,
            @Value("${app.ai.insight.api-key:${app.ai.terra.api-key:${OPENAI_API_KEY:}}}") String key) {
        return new ResponseTextInsightClient(http, properties, endpoint, key);
    }
}
