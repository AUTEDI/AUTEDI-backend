package com.ceos24.autedi.domain.generation.client;

import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockCorrectionAiClient implements CorrectionAiClient {

    @Override
    public List<CorrectionDraft> generate(
            Worksheet worksheet, List<WorksheetPage> pages, List<WorksheetSetting> settings) {
        log.info("[MockCorrectionAiClient] AI 첨삭 모의 생성 실행: worksheetId={}", worksheet.getId());

        WorksheetPage defaultPage = pages.isEmpty() ? null : pages.get(0);
        Long pageId = defaultPage != null ? defaultPage.getId() : null;
        Integer pageNumber = defaultPage != null ? defaultPage.getPageNumber() : 1;

        List<CorrectionDraft> drafts = new ArrayList<>();

        // 1. 문법 및 철자 더미 첨삭
        drafts.add(new CorrectionDraft(
                pageId,
                pageNumber,
                CorrectionArea.GRAMMAR_SPELLING,
                "He go to school yesterday.",
                "He went to school yesterday.",
                "과거 시점(yesterday)을 나타내는 문장이므로 과거형 동사 went를 사용해야 합니다.",
                List.of(new RegionPayload(0.20, 0.30, 0.15, 0.04))));

        // 2. 어휘 및 표현 더미 첨삭
        drafts.add(new CorrectionDraft(
                pageId,
                pageNumber,
                CorrectionArea.VOCABULARY_EXPRESSION,
                "I have a very big house.",
                "I have a spacious house.",
                "'very big' 대신 더 풍부한 묘사를 위해 'spacious(넓은)'라는 어휘를 추천합니다.",
                List.of(new RegionPayload(0.25, 0.45, 0.20, 0.04))));

        // 3. 문장 구성 더미 첨삭
        drafts.add(new CorrectionDraft(
                pageId,
                pageNumber,
                CorrectionArea.SENTENCE_STRUCTURE,
                "Because it was raining so we stayed home.",
                "Because it was raining, we stayed home.",
                "이유를 나타내는 종속접속사 Because와 등위접속사 so를 한 문장에서 중복으로 사용하지 않습니다.",
                List.of(new RegionPayload(0.20, 0.60, 0.35, 0.04))));

        return drafts;
    }
}
