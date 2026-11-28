package com.ceos24.autedi.domain.insight.client;

import com.ceos24.autedi.domain.generation.contract.v1.prompt.AiCorrectionPromptComposer;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.insight")
public record InsightClientProperties(String model, Duration requestTimeout, Integer maxOutputTokens) {
    public InsightClientProperties {
        model = model == null ? AiCorrectionPromptComposer.MODEL : model;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(60) : requestTimeout;
        maxOutputTokens = maxOutputTokens == null ? 2400 : maxOutputTokens;
        if (!model.matches("[A-Za-z0-9_.:-]{1,100}")
                || requestTimeout.toMillis() < 1
                || requestTimeout.compareTo(Duration.ofSeconds(90)) > 0
                || maxOutputTokens < 1
                || maxOutputTokens > 4000)
            throw new IllegalArgumentException("인사이트 모델·대기 시간(최대 90초)·출력량(최대 4000)을 확인해 주세요.");
    }
}
