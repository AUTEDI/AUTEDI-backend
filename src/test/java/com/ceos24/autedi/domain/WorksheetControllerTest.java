package com.ceos24.autedi.domain;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.dto.request.SettingItemRequest;
import com.ceos24.autedi.domain.worksheet.dto.request.WorksheetSettingsUpdateRequest;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionLevel;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSettingRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.ceos24.autedi.support.TestWorksheetImages;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class WorksheetControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AcademyRepository academyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorksheetRepository worksheetRepository;

    @Autowired
    private WorksheetPageRepository worksheetPageRepository;

    @Autowired
    private WorksheetSettingRepository worksheetSettingRepository;

    private Academy academyA;
    private Academy academyB;
    private User userAcademyA;
    private User userAcademyB;
    private String tokenA;
    private String tokenB;
    private Worksheet worksheetA;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        academyA = academyRepository.save(Academy.builder()
                .name("A 어학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(3))
                .build());

        academyB = academyRepository.save(Academy.builder()
                .name("B 어학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(3))
                .build());

        userAcademyA = userRepository.save(User.builder()
                .academy(academyA)
                .email("teacherA@autedi.com")
                .passwordHash("hashed")
                .name("선생님A")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());

        userAcademyB = userRepository.save(User.builder()
                .academy(academyB)
                .email("teacherB@autedi.com")
                .passwordHash("hashed")
                .name("선생님B")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());

        tokenA = "Bearer "
                + jwtTokenProvider.createAccessToken(
                        userAcademyA.getId(),
                        academyA.getId(),
                        userAcademyA.getEmail(),
                        userAcademyA.getRole().name());

        tokenB = "Bearer "
                + jwtTokenProvider.createAccessToken(
                        userAcademyB.getId(),
                        academyB.getId(),
                        userAcademyB.getEmail(),
                        userAcademyB.getRole().name());

        worksheetA = worksheetRepository.save(Worksheet.builder()
                .academy(academyA)
                .user(userAcademyA)
                .originalFilename("test_worksheet.png")
                .fileUrl("http://example.com/test_worksheet.png")
                .fileType(FileType.PNG)
                .fileSize(2048L)
                .fileHash("hash-worksheet-a")
                .pageCount(1)
                .build());

        worksheetPageRepository.save(WorksheetPage.builder()
                .worksheet(worksheetA)
                .pageNumber(1)
                .previewImageUrl("http://example.com/page1.png")
                .width(1200)
                .height(1600)
                .build());

        worksheetSettingRepository.save(WorksheetSetting.builder()
                .worksheet(worksheetA)
                .area(CorrectionArea.GRAMMAR_SPELLING)
                .level(CorrectionLevel.STANDARD)
                .build());
    }

    @Test
    @DisplayName("GET /api/v1/worksheets/{id} - 정상 조회 성공 및 ETag 헤더 반환")
    void getWorksheet_success() throws Exception {
        mockMvc.perform(get("/api/v1/worksheets/{worksheetId}", worksheetA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.id").value(worksheetA.getId()))
                .andExpect(jsonPath("$.result.file.name").value("test_worksheet.png"));
    }

    @Test
    @DisplayName("POST /api/v1/worksheets - 다중 파일(files) 업로드 시 2페이지 이상 생성 성공")
    void uploadWorksheets_multiPage_success() throws Exception {
        MockMultipartFile file1 =
                new MockMultipartFile("files", "page1.png", MediaType.IMAGE_PNG_VALUE, TestWorksheetImages.png(2, 3));
        MockMultipartFile file2 =
                new MockMultipartFile("files", "page2.png", MediaType.IMAGE_PNG_VALUE, TestWorksheetImages.png(4, 2));

        mockMvc.perform(multipart("/api/v1/worksheets")
                        .file(file1)
                        .file(file2)
                        .header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.pages.length()").value(2))
                .andExpect(jsonPath("$.result.pages[0].pageNumber").value(1))
                .andExpect(jsonPath("$.result.pages[1].pageNumber").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/worksheets - 'file' 파라미터로 여러 이미지를 동시에 전달해도 다중 페이지 생성 성공")
    void uploadWorksheets_multiPage_usingFileParam_success() throws Exception {
        MockMultipartFile file1 =
                new MockMultipartFile("file", "page1.png", MediaType.IMAGE_PNG_VALUE, TestWorksheetImages.png(2, 3));
        MockMultipartFile file2 =
                new MockMultipartFile("file", "page2.png", MediaType.IMAGE_PNG_VALUE, TestWorksheetImages.png(4, 2));

        mockMvc.perform(multipart("/api/v1/worksheets")
                        .file(file1)
                        .file(file2)
                        .header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.pages.length()").value(2))
                .andExpect(jsonPath("$.result.pages[0].pageNumber").value(1))
                .andExpect(jsonPath("$.result.pages[1].pageNumber").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/worksheets - 프론트엔드 'images' 파라미터로 여러 이미지를 전달해도 다중 페이지 생성 성공")
    void uploadWorksheets_multiPage_usingImagesParam_success() throws Exception {
        MockMultipartFile file1 =
                new MockMultipartFile("images", "sheet1.png", MediaType.IMAGE_PNG_VALUE, TestWorksheetImages.png(2, 3));
        MockMultipartFile file2 =
                new MockMultipartFile("images", "sheet2.png", MediaType.IMAGE_PNG_VALUE, TestWorksheetImages.png(4, 2));
        MockMultipartFile file3 =
                new MockMultipartFile("images", "sheet3.png", MediaType.IMAGE_PNG_VALUE, TestWorksheetImages.png(3, 2));

        mockMvc.perform(multipart("/api/v1/worksheets")
                        .file(file1)
                        .file(file2)
                        .file(file3)
                        .header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.pages.length()").value(3))
                .andExpect(jsonPath("$.result.pages[0].pageNumber").value(1))
                .andExpect(jsonPath("$.result.pages[1].pageNumber").value(2))
                .andExpect(jsonPath("$.result.pages[2].pageNumber").value(3));
    }

    @Test
    @DisplayName("POST /api/v1/worksheets - 빈 파일 전달 시 400 EMPTY_FILE 차단")
    void uploadWorksheet_emptyFile_throwsEmptyFile() throws Exception {
        MockMultipartFile emptyFile =
                new MockMultipartFile("file", "empty.png", MediaType.IMAGE_PNG_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/worksheets").file(emptyFile).header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("FILE_001"));
    }

    @Test
    @DisplayName("GET /api/v1/worksheets/{id} - [테넌트 격리] 타 학원 학습지 접근 시 404 자원 은닉")
    void getWorksheet_tenantIsolation_returnsNotFound() throws Exception {
        // Academy B의 유저가 Academy A의 학습지 상세 조회를 시도할 때 404 반환
        mockMvc.perform(get("/api/v1/worksheets/{worksheetId}", worksheetA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"));
    }

    @Test
    @DisplayName("GET /api/v1/worksheets/{id}/settings - [테넌트 격리] 타 학원 학습지 설정 조회 시 404 자원 은닉")
    void getSettings_tenantIsolation_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/worksheets/{worksheetId}/settings", worksheetA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"));
    }

    @Test
    @DisplayName("PUT /api/v1/worksheets/{id}/settings - DRAFT 상태에서 설정 교체 성공")
    void updateSettings_success() throws Exception {
        WorksheetSettingsUpdateRequest request = new WorksheetSettingsUpdateRequest(List.of(
                new SettingItemRequest(CorrectionArea.GRAMMAR_SPELLING, CorrectionLevel.ADVANCED),
                new SettingItemRequest(CorrectionArea.VOCABULARY_EXPRESSION, CorrectionLevel.STANDARD)));

        String etag = worksheetA.getEtag();

        mockMvc.perform(put("/api/v1/worksheets/{worksheetId}/settings", worksheetA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenA)
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.areas").isArray())
                .andExpect(jsonPath("$.result.areas.length()").value(2));
    }

    @Test
    @DisplayName("PUT /api/v1/worksheets/{id}/settings - [상태머신 방어] READY 상태에서 설정 수정 시 409 CONFLICT 차단")
    void updateSettings_stateMachineConflict_readyStatus() throws Exception {
        // 학습지를 READY 상태로 변경
        worksheetA.updateStatus(WorksheetStatus.READY);
        worksheetRepository.save(worksheetA);

        WorksheetSettingsUpdateRequest request = new WorksheetSettingsUpdateRequest(
                List.of(new SettingItemRequest(CorrectionArea.GRAMMAR_SPELLING, CorrectionLevel.ADVANCED)));

        mockMvc.perform(put("/api/v1/worksheets/{worksheetId}/settings", worksheetA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("WORKSHEET_002"));
    }

    @Test
    @DisplayName("PUT /api/v1/worksheets/{id}/settings - [상태머신 방어] PROCESSING 상태에서 설정 수정 시 409 CONFLICT 차단")
    void updateSettings_stateMachineConflict_processingStatus() throws Exception {
        worksheetA.updateStatus(WorksheetStatus.PROCESSING);
        worksheetRepository.save(worksheetA);

        WorksheetSettingsUpdateRequest request = new WorksheetSettingsUpdateRequest(
                List.of(new SettingItemRequest(CorrectionArea.GRAMMAR_SPELLING, CorrectionLevel.ADVANCED)));

        mockMvc.perform(put("/api/v1/worksheets/{worksheetId}/settings", worksheetA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("WORKSHEET_002"));
    }

    @Test
    @DisplayName("PUT /api/v1/worksheets/{id}/settings - [동시성 방어] If-Match 불일치 시 412 PRECONDITION_FAILED")
    void updateSettings_ifMatchMismatch_preconditionFailed() throws Exception {
        WorksheetSettingsUpdateRequest request = new WorksheetSettingsUpdateRequest(
                List.of(new SettingItemRequest(CorrectionArea.GRAMMAR_SPELLING, CorrectionLevel.ADVANCED)));

        mockMvc.perform(put("/api/v1/worksheets/{worksheetId}/settings", worksheetA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenA)
                        .header(HttpHeaders.IF_MATCH, "\"wrong-settings-etag\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_412"));
    }

    @Test
    @DisplayName("PUT /api/v1/worksheets/{id}/settings - [유효성 검증] 중복된 영역 지정 시 400 BAD_REQUEST")
    void updateSettings_duplicateAreas_badRequest() throws Exception {
        WorksheetSettingsUpdateRequest request = new WorksheetSettingsUpdateRequest(List.of(
                new SettingItemRequest(CorrectionArea.GRAMMAR_SPELLING, CorrectionLevel.STANDARD),
                new SettingItemRequest(CorrectionArea.GRAMMAR_SPELLING, CorrectionLevel.ADVANCED)));

        mockMvc.perform(put("/api/v1/worksheets/{worksheetId}/settings", worksheetA.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("PUT /api/v1/worksheets/{id}/file - [상태머신 방어] READY 상태에서 원본 파일 교체 시 409 CONFLICT 차단")
    void replaceFile_stateMachineConflict_readyStatus() throws Exception {
        worksheetA.updateStatus(WorksheetStatus.READY);
        worksheetRepository.save(worksheetA);

        MockMultipartFile file = new MockMultipartFile(
                "file", "new_sample.png", MediaType.IMAGE_PNG_VALUE, "dummy new image content".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/worksheets/{worksheetId}/file", worksheetA.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("WORKSHEET_002"));
    }

    @Test
    @DisplayName("PUT /api/v1/worksheets/{id}/file - [테넌트 격리] 타 학원 학습지 파일 교체 시 404 자원 은닉")
    void replaceFile_tenantIsolation_returnsNotFound() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "new_sample.png", MediaType.IMAGE_PNG_VALUE, "dummy new image content".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/worksheets/{worksheetId}/file", worksheetA.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"));
    }
}
