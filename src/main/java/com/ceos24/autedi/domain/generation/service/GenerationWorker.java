package com.ceos24.autedi.domain.generation.service;

import com.ceos24.autedi.domain.generation.client.CorrectionAiClient;
import com.ceos24.autedi.domain.generation.client.TerraGenerationClient;
import com.ceos24.autedi.domain.generation.contract.GenerationContractRouter;
import com.ceos24.autedi.domain.generation.entity.GenerationClientMode;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationWorker {
    private final GenerationWorkStore store;
    private final WorksheetService images;
    private final FixedResponseGenerationService fixed;
    private final CorrectionAiClient legacy;
    private final TerraGenerationClient terra;
    private final GenerationContractRouter contracts;

    /** 이 메서드와 클라이언트 대기에는 트랜잭션이 없다. DB 작업만 별도 빈에서 커밋한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(Long id) {
        try {
            var work = store.claim(id);
            if (work == null) return;
            var image = images.getPreparedImage(work.academyId(), work.worksheetId(), 1);
            var inputStep = store.prepareTranscription(work, image);
            if (inputStep == null) return;
            var transcription = inputStep.reused();
            if (transcription == null) {
                var lines = work.mode() == GenerationClientMode.TERRA
                        ? terra.transcribe(work.id(), image)
                        : fixed.transcribe(image, work.mode());
                transcription = store.transcribed(work, image, lines);
            }
            if (transcription == null) return;
            if (work.mode() == GenerationClientMode.LEGACY_MOCK) {
                var input = store.legacyInput(work);
                fixed.beforeCorrection();
                var drafts = legacy.generate(input.worksheet(), input.pages(), input.settings());
                store.completeLegacy(work, drafts);
                return;
            }
            String raw = work.mode() == GenerationClientMode.TERRA
                    ? terra.correct(work.id(), image, transcription, work.settings(), work.aiProfile())
                    : fixed.correct(image, transcription);
            try {
                contracts.assertReadable(work.aiProfile().version(), raw);
            } catch (IllegalArgumentException e) {
                store.unreadable(work, transcription, raw);
                return;
            }
            var input = new GenerationContractRouter.Input(image.pageNumber(), transcription.lines(), work.settings());
            var validated = contracts.parseAndValidate(work.aiProfile(), raw, input);
            store.complete(work, transcription, raw, validated);
        } catch (Exception e) {
            // 임의 예외 메시지에 응답 원문·인증정보가 있을 수 있어 저장·로그에 쓰지 않는다.
            boolean interrupted = Thread.interrupted();
            try {
                String code = e instanceof GenerationClientFailure failure ? failure.code() : "GENERATION_FAILED";
                boolean retryable = !(e instanceof GenerationClientFailure failure) || failure.retryable();
                store.fail(id, code, "생성 작업이 실패했습니다. 상태와 보존된 전사를 확인한 뒤 재시도해 주세요.", retryable);
                log.warn("생성 작업 실패: generationId={}, code={}", id, code);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }
}
