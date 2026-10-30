package com.ceos24.autedi.domain.worksheet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "학습지 첨삭 설정 전체 교체 요청")
public record WorksheetSettingsUpdateRequest(
        @Schema(description = "지정할 첨삭 영역 목록 (최소 1개, 최대 4개, 중복 불가)")
                @NotEmpty(message = "최소 한 개 이상의 첨삭 영역을 선택해야 합니다.")
                @Size(max = 4, message = "첨삭 영역은 최대 4개까지 선택 가능합니다.")
                List<@Valid SettingItemRequest> areas) {}
