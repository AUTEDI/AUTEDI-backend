package com.ceos24.autedi.domain.generation.client;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 키가 toString·로그에 포함되지 않게 record/Lombok Data를 사용하지 않는다. .env를 직접 읽지 않는다. */
@ConfigurationProperties("app.ai.terra")
public class TerraClientProperties {
    private URI endpoint = URI.create("https://api.openai.com/v1/responses");
    private String apiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(20);
    private Duration requestTimeout = Duration.ofMinutes(3);
    private int maxOutputTokens = 12000;

    public URI getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(URI value) {
        endpoint = value;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String value) {
        apiKey = value;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration value) {
        connectTimeout = value;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration value) {
        requestTimeout = value;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int value) {
        maxOutputTokens = value;
    }
}
