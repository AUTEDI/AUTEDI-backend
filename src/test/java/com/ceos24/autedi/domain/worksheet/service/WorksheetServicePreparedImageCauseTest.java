package com.ceos24.autedi.domain.worksheet.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ceos24.autedi.domain.auth.repository.QrSessionRepository;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.policy.WorksheetAccessPolicy;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSettingRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.storage.FileStorageService;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;

class WorksheetServicePreparedImageCauseTest {

    @Test
    @DisplayName("보정 PNG를 읽다가 IOException이 나면 FILE_STORAGE_FAILED에 원인이 담긴다")
    void readFailureKeepsCause() throws Exception {
        WorksheetRepository worksheetRepository = mock(WorksheetRepository.class);
        WorksheetPageRepository pageRepository = mock(WorksheetPageRepository.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        WorksheetService service = new WorksheetService(
                worksheetRepository,
                new WorksheetAccessPolicy(worksheetRepository),
                pageRepository,
                mock(WorksheetSettingRepository.class),
                mock(WorksheetGenerationRepository.class),
                mock(UserRepository.class),
                mock(StudentRepository.class),
                fileStorageService,
                mock(QrSessionRepository.class),
                mock(ApplicationEventPublisher.class));

        WorksheetPage page = mock(WorksheetPage.class);
        when(page.getPreviewImageUrl()).thenReturn("worksheets/1/id-prepared.png");
        when(worksheetRepository.findByIdAndAcademyId(7L, 1L)).thenReturn(Optional.of(mock(Worksheet.class)));
        when(pageRepository.findByWorksheetIdAndPageNumber(7L, 1)).thenReturn(Optional.of(page));
        IOException ioFailure = new IOException("disk read error");
        Resource unreadable = mock(Resource.class);
        when(unreadable.getInputStream()).thenThrow(ioFailure);
        when(fileStorageService.load("worksheets/1/id-prepared.png")).thenReturn(unreadable);

        assertThatThrownBy(() -> service.getPreparedImage(1L, 7L, 1))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_STORAGE_FAILED)
                .hasCause(ioFailure);
    }
}
