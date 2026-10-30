package com.ceos24.autedi.domain.worksheet.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WorksheetSettingsUpdateRequest(
        @NotEmpty(message = "최소 한 개 이상의 첨삭 영역을 선택해야 합니다.") @Size(max = 4, message = "첨삭 영역은 최대 4개까지 선택 가능합니다.")
                List<@Valid SettingItemRequest> areas) {}
