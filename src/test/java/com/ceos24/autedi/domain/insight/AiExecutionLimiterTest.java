package com.ceos24.autedi.domain.insight;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ceos24.autedi.domain.generation.client.*;
import com.ceos24.autedi.domain.generation.contract.GenerationContractRouter;
import com.ceos24.autedi.domain.generation.service.*;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
import org.junit.jupiter.api.Test;

class AiExecutionLimiterTest {
    @Test
    void sharesBudgetAndAllowsAtMostOneInsightAndReleasesExactlyOnce() {
        var limiter = new AiExecutionLimiter(2);
        var insight = limiter.tryInsight();
        assertThat(insight).isNotNull();
        assertThat(limiter.tryInsight()).isNull();
        var correction = limiter.tryCorrection();
        assertThat(correction).isNotNull();
        assertThat(limiter.tryCorrection()).isNull();
        insight.close();
        insight.close();
        var second = limiter.tryCorrection();
        assertThat(second).isNotNull();
        assertThat(limiter.tryCorrection()).isNull();
        correction.close();
        second.close();
        try (var again = limiter.tryInsight()) {
            assertThat(again).isNotNull();
        }
    }

    @Test
    void refusedCorrectionIsNotClaimedOrFailedAndCanBeDispatchedLater() {
        var limiter = new AiExecutionLimiter(1);
        var store = mock(GenerationWorkStore.class);
        var worker = new GenerationWorker(
                store,
                mock(WorksheetService.class),
                mock(FixedResponseGenerationService.class),
                mock(CorrectionAiClient.class),
                mock(TerraGenerationClient.class),
                mock(GenerationContractRouter.class),
                limiter);
        try (var insight = limiter.tryInsight()) {
            worker.process(1L);
            verifyNoInteractions(store);
        }
        worker.process(1L);
        verify(store).claim(1L);
        verifyNoMoreInteractions(store);
        try (var insight = limiter.tryInsight()) {
            assertThat(insight).isNotNull();
        }
    }

    @Test
    void failedCorrectionReleasesSharedBudget() {
        var limiter = new AiExecutionLimiter(1);
        var store = mock(GenerationWorkStore.class);
        when(store.claim(1L)).thenThrow(new IllegalStateException());
        var worker = new GenerationWorker(
                store,
                mock(WorksheetService.class),
                mock(FixedResponseGenerationService.class),
                mock(CorrectionAiClient.class),
                mock(TerraGenerationClient.class),
                mock(GenerationContractRouter.class),
                limiter);
        worker.process(1L);
        try (var permit = limiter.tryInsight()) {
            assertThat(permit).isNotNull();
        }
        verify(store).fail(eq(1L), eq("GENERATION_FAILED"), anyString(), eq(true));
    }
}
