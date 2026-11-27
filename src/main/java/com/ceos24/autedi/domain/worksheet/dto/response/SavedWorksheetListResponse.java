package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.worksheet.repository.SavedWorksheetRow;
import java.util.List;
import org.springframework.data.domain.Page;

public record SavedWorksheetListResponse(
        List<SavedWorksheetItemResponse> items, long total, int page, int size, int totalPages, boolean hasNext) {
    public static SavedWorksheetListResponse from(Page<SavedWorksheetRow> rows, Long userId) {
        return new SavedWorksheetListResponse(
                rows.getContent().stream()
                        .map(row -> SavedWorksheetItemResponse.from(row, userId))
                        .toList(),
                rows.getTotalElements(),
                rows.getNumber(),
                rows.getSize(),
                rows.getTotalPages(),
                rows.hasNext());
    }
}
