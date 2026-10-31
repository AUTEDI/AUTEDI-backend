package com.ceos24.autedi.domain.worksheet.service;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.dto.request.SettingItemRequest;
import com.ceos24.autedi.domain.worksheet.dto.request.WorksheetSettingsUpdateRequest;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetDetailResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetFileUrls;
import com.ceos24.autedi.domain.worksheet.dto.response.WorksheetSettingsResponse;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSettingRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.storage.FileStorageService;
import com.ceos24.autedi.global.storage.LocalStorageService;
import com.ceos24.autedi.global.storage.StoredFile;
import com.ceos24.autedi.global.storage.WorksheetImagePreparer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorksheetService {

    private final WorksheetRepository worksheetRepository;
    private final WorksheetPageRepository worksheetPageRepository;
    private final WorksheetSettingRepository worksheetSettingRepository;
    private final WorksheetGenerationRepository worksheetGenerationRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public WorksheetDetailResponse uploadWorksheet(Long userId, Long academyId, MultipartFile file) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Academy academy = user.getAcademy();
        if (!academy.getId().equals(academyId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        // 유료 구독 상태 검증
        if (!academy.isActive()) {
            throw new CustomException(ErrorCode.ACADEMY_SUBSCRIPTION_INACTIVE);
        }

        // 파일 저장 및 메타데이터 추출
        StoredFile storedFile = storeImage(file, academyId);

        // 학습지 마스터 레코드 생성 (초기 상태: DRAFT)
        Worksheet worksheet = Worksheet.builder()
                .academy(academy)
                .user(user)
                .originalFilename(storedFile.originalFilename())
                .fileUrl(storedFile.fileUrl())
                .fileType(storedFile.fileType())
                .fileSize(storedFile.fileSize())
                .fileHash(storedFile.fileHash())
                .pageCount(1)
                .build();
        worksheet = worksheetRepository.save(worksheet);

        // 기본 1페이지 생성
        WorksheetPage page = WorksheetPage.builder()
                .worksheet(worksheet)
                .pageNumber(1)
                .previewImageUrl(storedFile.preparedImageUrl())
                .width(storedFile.width())
                .height(storedFile.height())
                .build();
        worksheetPageRepository.save(page);

        log.info("학습지 업로드 완료: worksheetId={}, originalFilename={}", worksheet.getId(), worksheet.getOriginalFilename());

        return WorksheetDetailResponse.of(worksheet, List.of(page), List.of(), null);
    }

    public WorksheetDetailResponse getWorksheetDetail(Long academyId, Long worksheetId) {
        Worksheet worksheet = worksheetRepository
                .findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));

        List<WorksheetPage> pages = worksheetPageRepository.findAllByWorksheetIdOrderByPageNumberAsc(worksheetId);
        List<WorksheetSetting> settings = worksheetSettingRepository.findAllByWorksheetId(worksheetId);
        Long latestGenerationId = worksheetGenerationRepository
                .findFirstByWorksheetIdOrderByCreatedAtDescIdDesc(worksheetId)
                .map(WorksheetGeneration::getId)
                .orElse(null);

        return WorksheetDetailResponse.of(worksheet, pages, settings, latestGenerationId);
    }

    @Transactional
    public WorksheetDetailResponse replaceWorksheetFile(
            Long academyId, Long worksheetId, String ifMatch, MultipartFile file) {
        Worksheet worksheet = worksheetRepository
                .findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));

        // If-Match 헤더 검증 (제공된 경우)
        if (StringUtils.hasText(ifMatch) && !ifMatch.equals("*")) {
            if (!worksheet.getEtag().equals(ifMatch.trim())) {
                throw new CustomException(ErrorCode.PRECONDITION_FAILED);
            }
        }

        // 상태 제약 검증: DRAFT 또는 FAILED 상태에서만 파일 교체 가능
        if (!worksheet.canModifyOrReplace()) {
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE);
        }

        // 새 파일 저장 및 해시 추출
        StoredFile storedFile = storeImage(file, academyId);

        // 학습지 파일 정보 교체 (상태는 DRAFT로 재설정)
        worksheet.replaceFile(
                storedFile.originalFilename(),
                storedFile.fileUrl(),
                storedFile.fileType(),
                storedFile.fileSize(),
                storedFile.fileHash());

        // 페이지 미리보기 갱신 및 기존 OCR 캐시 무효화
        List<WorksheetPage> pages = worksheetPageRepository.findAllByWorksheetIdOrderByPageNumberAsc(worksheetId);
        if (!pages.isEmpty()) {
            WorksheetPage page1 = pages.get(0);
            page1.updatePreview(storedFile.preparedImageUrl(), storedFile.width(), storedFile.height());
            page1.updateOcrResult(null, null);
        } else {
            WorksheetPage newPage = WorksheetPage.builder()
                    .worksheet(worksheet)
                    .pageNumber(1)
                    .previewImageUrl(storedFile.preparedImageUrl())
                    .width(storedFile.width())
                    .height(storedFile.height())
                    .build();
            worksheetPageRepository.save(newPage);
            pages = List.of(newPage);
        }

        // 기존 첨삭 설정은 유지
        List<WorksheetSetting> settings = worksheetSettingRepository.findAllByWorksheetId(worksheetId);

        log.info("학습지 파일 교체 완료: worksheetId={}, newFilename={}", worksheet.getId(), worksheet.getOriginalFilename());

        return WorksheetDetailResponse.of(worksheet, pages, settings, null);
    }

    public WorksheetSettingsResponse getSettings(Long academyId, Long worksheetId) {
        Worksheet worksheet = worksheetRepository
                .findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));

        List<WorksheetSetting> settings = worksheetSettingRepository.findAllByWorksheetId(worksheetId);
        String etag = "\"settings_" + worksheet.getId() + "-v" + worksheet.getVersion() + "\"";
        return WorksheetSettingsResponse.of(etag, settings);
    }

    public WorksheetFileResource getOriginalFile(Long academyId, Long worksheetId) {
        Worksheet worksheet = findOwnedWorksheet(academyId, worksheetId);
        String mediaType =
                switch (worksheet.getFileType()) {
                    case JPG, JPEG -> "image/jpeg";
                    case PNG -> "image/png";
                    case PDF -> "application/pdf"; // 이전에 저장한 원본 다운로드만 허용하며 PDF를 처리하지 않는다.
                };
        return new WorksheetFileResource(
                worksheet.getOriginalFilename(),
                mediaType,
                worksheet.getFileHash(),
                fileStorageService.load(worksheet.getFileUrl()));
    }

    /** 화면 API와 향후 두 AI 단계가 함께 사용하는 단일 저장 PNG 입력이다. AI 요청은 수행하지 않는다. */
    public PreparedWorksheetImage getPreparedImage(Long academyId, Long worksheetId, int pageNumber) {
        findOwnedWorksheet(academyId, worksheetId);
        WorksheetPage page = worksheetPageRepository
                .findByWorksheetIdAndPageNumber(worksheetId, pageNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.PAGE_NOT_FOUND));
        if (!page.getPreviewImageUrl().endsWith("-prepared.png"))
            throw new CustomException(ErrorCode.IMAGE_NOT_PREPARED);
        try (var input = fileStorageService.load(page.getPreviewImageUrl()).getInputStream()) {
            byte[] png = input.readNBytes(WorksheetImagePreparer.MAX_BYTES + 1);
            if (png.length > WorksheetImagePreparer.MAX_BYTES || png.length < 24 || !WorksheetImagePreparer.isPng(png))
                throw new CustomException(ErrorCode.INVALID_IMAGE);
            ByteBuffer header = ByteBuffer.wrap(png);
            if (header.getInt(16) != page.getWidth() || header.getInt(20) != page.getHeight())
                throw new CustomException(ErrorCode.INVALID_IMAGE, "저장된 PNG 크기와 페이지 정보가 일치하지 않습니다.");
            return new PreparedWorksheetImage(
                    worksheetId,
                    page.getId(),
                    pageNumber,
                    WorksheetFileUrls.image(page.getPreviewImageUrl(), worksheetId, pageNumber),
                    page.getWidth(),
                    page.getHeight(),
                    LocalStorageService.sha256(png),
                    png);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    private Worksheet findOwnedWorksheet(Long academyId, Long worksheetId) {
        return worksheetRepository
                .findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));
    }

    private StoredFile storeImage(MultipartFile file, Long academyId) {
        StoredFile stored = fileStorageService.upload(file, "worksheets/" + academyId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) return;
                    for (String url : List.of(stored.preparedImageUrl(), stored.fileUrl())) {
                        try {
                            fileStorageService.delete(url);
                        } catch (RuntimeException e) {
                            log.warn("롤백된 업로드의 파일 정리를 완료하지 못했습니다.");
                        }
                    }
                }
            });
        }
        return stored;
    }

    @Transactional
    public WorksheetSettingsResponse updateSettings(
            Long academyId, Long worksheetId, String ifMatch, WorksheetSettingsUpdateRequest request) {
        Worksheet worksheet = worksheetRepository
                .findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));

        // 상태 검증: DRAFT 또는 FAILED 상태에서만 설정 변경 가능
        if (!worksheet.canModifyOrReplace()) {
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE);
        }

        // If-Match 헤더 검증
        String currentSettingsEtag = "\"settings_" + worksheet.getId() + "-v" + worksheet.getVersion() + "\"";
        if (StringUtils.hasText(ifMatch) && !ifMatch.equals("*")) {
            if (!currentSettingsEtag.equals(ifMatch.trim())
                    && !worksheet.getEtag().equals(ifMatch.trim())) {
                throw new CustomException(ErrorCode.PRECONDITION_FAILED);
            }
        }

        // 영역 중복 검증
        long distinctAreas = request.areas().stream()
                .map(SettingItemRequest::area)
                .distinct()
                .count();
        if (distinctAreas != request.areas().size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "중복된 첨삭 영역을 지정할 수 없습니다.");
        }

        // 기존 설정 전체 교체
        worksheetSettingRepository.deleteAllByWorksheetId(worksheetId);
        worksheetSettingRepository.flush();

        List<WorksheetSetting> newSettings = request.areas().stream()
                .map(item -> WorksheetSetting.builder()
                        .worksheet(worksheet)
                        .area(item.area())
                        .level(item.level())
                        .build())
                .toList();
        worksheetSettingRepository.saveAll(newSettings);

        log.info("학습지 첨삭 설정 교체 완료: worksheetId={}, areaCount={}", worksheetId, newSettings.size());

        String newSettingsEtag = "\"settings_" + worksheet.getId() + "-v" + (worksheet.getVersion() + 1) + "\"";
        return WorksheetSettingsResponse.of(newSettingsEtag, newSettings);
    }
}
