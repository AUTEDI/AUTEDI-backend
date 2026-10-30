package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import java.time.LocalDateTime;
import java.util.List;

public record WorksheetDetailResponse(
        Long id,
        String etag,
        WorksheetStatus status,
        FileDetailResponse file,
        List<PageDetailResponse> pages,
        WorksheetSettingsResponse settings,
        Long latestGenerationId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static WorksheetDetailResponse of(
            Worksheet worksheet,
            List<WorksheetPage> pages,
            List<WorksheetSetting> settings,
            Long latestGenerationId
    ) {
        String mediaType = switch (worksheet.getFileType()) {
            case JPG, JPEG -> "image/jpeg";
            case PNG -> "image/png";
            case PDF -> "application/pdf";
        };

        FileDetailResponse fileDetail = new FileDetailResponse(
                worksheet.getOriginalFilename(),
                mediaType,
                worksheet.getFileSize(),
                worksheet.getFileUrl(),
                null
        );

        List<PageDetailResponse> pageDetails = pages != null
                ? pages.stream()
                .map(p -> new PageDetailResponse(
                        p.getId(),
                        p.getPageNumber(),
                        p.getWidth(),
                        p.getHeight(),
                        p.getPreviewImageUrl(),
                        null
                ))
                .toList()
                : List.of();

        String settingsEtag = "\"settings_" + worksheet.getId() + "-v" + worksheet.getVersion() + "\"";
        WorksheetSettingsResponse settingsResponse = WorksheetSettingsResponse.of(settingsEtag, settings);

        return new WorksheetDetailResponse(
                worksheet.getId(),
                worksheet.getEtag(),
                worksheet.getStatus(),
                fileDetail,
                pageDetails,
                settingsResponse,
                latestGenerationId,
                worksheet.getCreatedAt(),
                worksheet.getUpdatedAt()
        );
    }
}
