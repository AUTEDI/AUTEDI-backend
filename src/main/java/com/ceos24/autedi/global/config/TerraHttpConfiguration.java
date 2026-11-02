package com.ceos24.autedi.global.config;

import com.ceos24.autedi.domain.generation.client.TerraClientProperties;
import com.ceos24.autedi.domain.generation.client.TerraGenerationClient;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TerraClientProperties.class)
public class TerraHttpConfiguration {
    @Bean
    public TerraGenerationClient terraGenerationClient(
            HttpClient terraHttpClient,
            TerraClientProperties properties,
            @Value("${app.ai.terra.api-key:${OPENAI_API_KEY:}}") String key) {
        properties.setApiKey(key);
        return new TerraGenerationClient(terraHttpClient, properties);
    }

    @Bean(destroyMethod = "shutdownNow")
    public HttpClient terraHttpClient(TerraClientProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
