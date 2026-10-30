package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import java.util.List;

public record WorksheetSettingsResponse(
        String etag,
        List<SettingItemResponse> areas
) {
    public static WorksheetSettingsResponse of(String etag, List<WorksheetSetting> settings) {
        List<SettingItemResponse> items = settings != null
                ? settings.stream()
                .map(s -> new SettingItemResponse(s.getArea(), s.getLevel()))
                .toList()
                : List.of();
        return new WorksheetSettingsResponse(etag, items);
    }

    public static WorksheetSettingsResponse from(List<WorksheetSetting> settings) {
        return of(null, settings);
    }
}
