package com.ceos24.autedi.domain.generation.persistence;

import java.util.List;

public record StoredTranscription(
        Long id,
        Long createdByGenerationId,
        Long worksheetId,
        Long pageId,
        int pageNumber,
        String sourceFileHash,
        String sourceImageUrl,
        String sourceImageHash,
        int imageWidth,
        int imageHeight,
        String model,
        String normalizationVersion,
        String textHash,
        List<String> lines) {
    public StoredTranscription {
        lines = List.copyOf(lines);
    }
}
