package com.ceos24.autedi.domain.generation.service;

import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationContext;
import com.ceos24.autedi.domain.generation.entity.GenerationClientMode;
import com.ceos24.autedi.domain.generation.entity.GenerationStage;
import com.ceos24.autedi.domain.generation.persistence.StoredTranscription;
import com.ceos24.autedi.domain.worksheet.service.PreparedWorksheetImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 외부 통신 없는 고정 클라이언트. 지연·실패는 이 경계에서만 재현한다. */
@Service
public class FixedResponseGenerationService {
    public static final String MODEL = "fixed-response";
    public static final String PROMPT_VERSION = "AUTEDI-FIXED-RESPONSE-v1";

    @Value("${app.ai.fixed-response.ocr-delay-ms:0}")
    private long ocrDelayMs;

    @Value("${app.ai.fixed-response.correction-delay-ms:0}")
    private long correctionDelayMs;

    @Value("${app.ai.fixed-response.fail-at:NONE}")
    private String failAt;

    public List<String> transcribe(PreparedWorksheetImage image, GenerationClientMode mode) {
        simulate(GenerationStage.OCR);
        String text = mode == GenerationClientMode.FIXED
                ? resource("transcription.txt")
                : "He go to school yesterday.\nI have a very big house.\nBecause it was raining so we stayed home.";
        return AiValidationContext.fromText(image.pageNumber(), text, Map.of()).transcriptionLines();
    }

    public String correct(PreparedWorksheetImage image, StoredTranscription transcription) {
        beforeCorrection();
        return resource("response.json");
    }

    public void beforeCorrection() {
        simulate(GenerationStage.AI_CORRECTION);
    }

    private void simulate(GenerationStage stage) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("클라이언트 대기 중 DB 트랜잭션을 열 수 없습니다.");
        long delay = stage == GenerationStage.OCR ? ocrDelayMs : correctionDelayMs;
        try {
            if (delay < 0) throw new IllegalArgumentException("지연은 0 이상이어야 합니다.");
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationClientFailure("WORKER_INTERRUPTED", "작업 실행이 중단되었습니다.");
        }
        if (stage.name().equalsIgnoreCase(failAt))
            throw new GenerationClientFailure(stage.name() + "_FAILED", "고정 클라이언트의 " + stage + " 실패입니다.");
    }

    private static String resource(String name) {
        try (var stream = FixedResponseGenerationService.class.getResourceAsStream("/ai/fixed/v1/" + name)) {
            if (stream == null) throw new IllegalStateException("고정 응답 리소스가 없습니다: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("고정 응답 리소스를 읽을 수 없습니다.", e);
        }
    }
}
