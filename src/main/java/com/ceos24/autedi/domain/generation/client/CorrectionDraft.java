package com.ceos24.autedi.domain.generation.client;

import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import java.util.List;

public record CorrectionDraft(
        Long pageId,
        Integer pageNumber,
        CorrectionArea area,
        String originalText,
        String aiInitialContent,
        String explanation,
        List<RegionPayload> regions) {}
