package com.ceos24.autedi.domain.worksheet.service;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.auth.entity.QrSession;
import com.ceos24.autedi.domain.auth.repository.QrSessionRepository;
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
import com.ceos24.autedi.domain.worksheet.event.WorksheetCreated;
import com.ceos24.autedi.domain.worksheet.event.WorksheetFileReplaced;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final QrSessionRepository qrSessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public WorksheetDetailResponse uploadWorksheets(Long userId, Long academyId, List<MultipartFile> files) {
        return uploadWorksheets(userId, academyId, null, files);
    }

    /**
     * @param qrSessionId QR 로그인 토큰으로 호출한 경우 그 QR 세션. 일반 호출은 null이며 동작이 달라지지 않는다.
     */
    @Transactional
    public WorksheetDetailResponse uploadWorksheets(
            Long userId, Long academyId, String qrSessionId, List<MultipartFile> files) {
        QrSession qr = resolveQrTarget(qrSessionId, userId);
        if (qr != null && qr.getWorksheetId() != null) {
            // 기존 작업 교체용으로 만든 QR로는 새 작업을 만들 수 없다.
            throw new CustomException(ErrorCode.QR_TARGET_MISMATCH);
        }
        if (files == null || files.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_FILE);
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Academy academy = user.getAcademy();
        if (!academy.getId().equals(academyId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        // 유료 구독 상태 검증
        if (!academy.isActive()) {
            throw new CustomException(ErrorCode.ACADEMY_SUBSCRIPTION_INACTIVE);
        }

        // 각 파일 저장 및 메타데이터 추출
        List<StoredFile> storedFiles = new java.util.ArrayList<>();
        long totalSize = 0L;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            StoredFile sf = storeImage(file, academyId);
            storedFiles.add(sf);
            totalSize += sf.fileSize();
        }

        if (storedFiles.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_FILE);
        }

        StoredFile firstFile = storedFiles.get(0);

        // 학습지 마스터 레코드 생성 (초기 상태: DRAFT, pageCount: 업로드된 파일 개수)
        Worksheet worksheet = Worksheet.builder()
                .academy(academy)
                .user(user)
                .originalFilename(firstFile.originalFilename())
                .fileUrl(firstFile.fileUrl())
                .fileType(firstFile.fileType())
                .fileSize(totalSize)
                .fileHash(firstFile.fileHash())
                .pageCount(storedFiles.size())
                .build();
        Worksheet savedWorksheet = worksheetRepository.save(worksheet);

        // 순서대로 페이지(1, 2, ...) 생성
        List<WorksheetPage> pages = new java.util.ArrayList<>();
        for (int i = 0; i < storedFiles.size(); i++) {
            StoredFile sf = storedFiles.get(i);
            WorksheetPage page = WorksheetPage.builder()
                    .worksheet(savedWorksheet)
                    .pageNumber(i + 1)
                    .previewImageUrl(sf.preparedImageUrl())
                    .width(sf.width())
                    .height(sf.height())
                    .build();
            worksheetPageRepository.save(page);
            pages.add(page);
        }

        log.info(
                "학습지 다중 페이지 업로드 완료: worksheetId={}, originalFilename={}, pageCount={}",
                savedWorksheet.getId(),
                savedWorksheet.getOriginalFilename(),
                pages.size());

        if (qr != null) {
            eventPublisher.publishEvent(new WorksheetCreated(userId, qr.getSessionId(), savedWorksheet.getId()));
        }

        return WorksheetDetailResponse.of(savedWorksheet, pages, List.of(), null);
    }

    @Transactional
    public WorksheetDetailResponse uploadWorksheet(Long userId, Long academyId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_FILE);
        }
        return uploadWorksheets(userId, academyId, List.of(file));
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
        return replaceWorksheetFile(academyId, null, null, worksheetId, ifMatch, file);
    }

    /**
     * @param qrSessionId QR 로그인 토큰으로 호출한 경우 그 QR 세션. 일반 호출은 null이며 동작이 달라지지 않는다.
     */
    @Transactional
    public WorksheetDetailResponse replaceWorksheetFile(
            Long academyId, Long userId, String qrSessionId, Long worksheetId, String ifMatch, MultipartFile file) {
        Worksheet worksheet = worksheetRepository
                .findByIdAndAcademyId(worksheetId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKSHEET_NOT_FOUND));

        QrSession qr = resolveQrTarget(qrSessionId, userId);
        if (qr != null) {
            boolean notOwner = !worksheet.getUser().getId().equals(qr.getUserId());
            boolean otherTarget =
                    qr.getWorksheetId() != null && !qr.getWorksheetId().equals(worksheetId);
            if (notOwner || otherTarget) {
                throw new CustomException(ErrorCode.QR_TARGET_MISMATCH);
            }
        }

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

        if (worksheet.getPageCount() != 1)
            throw new CustomException(ErrorCode.INVALID_WORKSHEET_STATE, "다중 페이지 학습지의 단일 파일 교체는 지원하지 않습니다.");

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

        if (qr != null) {
            eventPublisher.publishEvent(
                    new WorksheetFileReplaced(worksheet.getUser().getId(), qr.getSessionId(), worksheetId));
        }

        return WorksheetDetailResponse.of(worksheet, pages, settings, null);
    }

    /**
     * QR 로그인 토큰이면 연결된 QR 세션을 반환한다. 일반 토큰(null)이면 null이다. QR 표시가 있는데 세션 행이 이미 정리됐다면 일반 요청으로
     * 취급하지 않고 QR_002로 거절해 QR을 다시 찍게 한다. 세션 소유자와 토큰 사용자가 다르면 거부한다.
     */
    private QrSession resolveQrTarget(String qrSessionId, Long userId) {
        if (qrSessionId == null) {
            return null;
        }
        QrSession qr = qrSessionRepository
                .findBySessionId(qrSessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.QR_SESSION_EXPIRED));
        if (userId != null && !qr.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.QR_TARGET_MISMATCH);
        }
        return qr;
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
