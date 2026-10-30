package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "학습지 첨삭 설정 목록 응답")
public record WorksheetSettingsResponse(
        @Schema(description = "설정 동시성 제어용 ETag", example = "\"settings_1-v1\"") String etag,
        @Schema(description = "저장된 첨삭 영역 및 수준 목록") List<SettingItemResponse> areas) {
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
