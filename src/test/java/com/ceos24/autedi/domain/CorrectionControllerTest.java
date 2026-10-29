package com.ceos24.autedi.domain;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.comment.entity.Comment;
import com.ceos24.autedi.domain.comment.entity.HighlightType;
import com.ceos24.autedi.domain.comment.repository.CommentRepository;
import com.ceos24.autedi.domain.correction.dto.request.CorrectionUpdateRequest;
import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.generation.entity.GenerationStage;
import com.ceos24.autedi.domain.generation.entity.GenerationStatus;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class CorrectionControllerTest {

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
    private WorksheetGenerationRepository generationRepository;

    @Autowired
    private CorrectionRepository correctionRepository;

    @Autowired
    private CommentRepository commentRepository;

    private User testUser;
    private Academy testAcademy;
    private Worksheet testWorksheet;
    private WorksheetPage testPage;
    private WorksheetGeneration testGeneration;
    private Correction testCorrection;
    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        testAcademy = academyRepository.save(Academy.builder()
                .name("테스트 어학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusDays(30))
                .build());

        testUser = userRepository.save(User.builder()
                .academy(testAcademy)
                .email("teacher@test.com")
                .passwordHash("hashed")
                .name("김선생")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());

        token = "Bearer " + jwtTokenProvider.createAccessToken(
                testUser.getId(),
                testAcademy.getId(),
                testUser.getEmail(),
                testUser.getRole().name()
        );

        testWorksheet = worksheetRepository.save(Worksheet.builder()
                .academy(testAcademy)
                .user(testUser)
                .originalFilename("sample.png")
                .fileUrl("http://example.com/sample.png")
                .fileType(FileType.PNG)
                .fileSize(1024L)
                .fileHash("hash123")
                .pageCount(1)
                .build());

        // Set worksheet to READY
        testWorksheet.updateStatus(WorksheetStatus.READY);

        testPage = worksheetPageRepository.save(WorksheetPage.builder()
                .worksheet(testWorksheet)
                .pageNumber(1)
                .previewImageUrl("http://example.com/preview1.png")
                .width(1000)
                .height(1400)
                .build());

        testGeneration = WorksheetGeneration.builder()
                .worksheet(testWorksheet)
                .requestedBy(testUser)
                .idempotencyKey("test-key-123")
                .requestFileHash("hash123")
                .requestSettingsSnapshot(List.of("GRAMMAR_SPELLING"))
                .retryCount(0)
                .build();
        testGeneration.succeed();
        testGeneration = generationRepository.save(testGeneration);

        testCorrection = correctionRepository.save(Correction.builder()
                .worksheet(testWorksheet)
                .page(testPage)
                .generation(testGeneration)
                .area(CorrectionArea.GRAMMAR_SPELLING)
                .originalText("He go")
                .aiInitialContent("He goes")
                .content("He goes")
                .explanation("3인칭 단수 주어에는 goes를 씁니다.")
                .regions(List.of(new RegionPayload(0.1, 0.2, 0.3, 0.05)))
                .build());
    }

    @Test
    @DisplayName("GET /review - 검토 화면 통합 조회 성공 (학습지, 생성작업, 첨삭, 코멘트)")
    void getReview_success() throws Exception {
        commentRepository.save(Comment.builder()
                .worksheet(testWorksheet)
                .page(testPage)
                .user(testUser)
                .highlightType(HighlightType.HIGHLIGHT)
                .regions(List.of(new RegionPayload(0.1, 0.2, 0.3, 0.05)))
                .content("교사 코멘트입니다.")
                .build());

        mockMvc.perform(get("/api/v1/worksheets/{worksheetId}/review", testWorksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.worksheet.id").value(testWorksheet.getId()))
                .andExpect(jsonPath("$.result.generation.id").value(testGeneration.getId()))
                .andExpect(jsonPath("$.result.corrections[0].id").value(testCorrection.getId()))
                .andExpect(jsonPath("$.result.corrections[0].originalText").value("He go"))
                .andExpect(jsonPath("$.result.comments[0].content").value("교사 코멘트입니다."));
    }

    @Test
    @DisplayName("GET /corrections - 첨삭 목록 필터 조회 성공")
    void getCorrections_success() throws Exception {
        mockMvc.perform(get("/api/v1/worksheets/{worksheetId}/corrections", testWorksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.total").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(testCorrection.getId()));
    }

    @Test
    @DisplayName("GET /corrections/{correctionId} - 첨삭 단건 조회 및 ETag 헤더 반환")
    void getCorrection_success() throws Exception {
        mockMvc.perform(get("/api/v1/worksheets/{worksheetId}/corrections/{correctionId}",
                        testWorksheet.getId(), testCorrection.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.result.content").value("He goes"));
    }

    @Test
    @DisplayName("PATCH /corrections/{correctionId} - If-Match 일치 시 내용 수정 성공")
    void updateCorrection_success() throws Exception {
        String etag = testCorrection.getEtag();
        CorrectionUpdateRequest request = new CorrectionUpdateRequest("He went to school");

        mockMvc.perform(patch("/api/v1/worksheets/{worksheetId}/corrections/{correctionId}",
                        testWorksheet.getId(), testCorrection.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content").value("He went to school"))
                .andExpect(jsonPath("$.result.editedByTeacher").value(true));
    }

    @Test
    @DisplayName("PATCH /corrections/{correctionId} - If-Match 불일치 시 412 Precondition Failed")
    void updateCorrection_preconditionFailed() throws Exception {
        CorrectionUpdateRequest request = new CorrectionUpdateRequest("He went to school");

        mockMvc.perform(patch("/api/v1/worksheets/{worksheetId}/corrections/{correctionId}",
                        testWorksheet.getId(), testCorrection.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, "\"wrong-etag\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    @DisplayName("DELETE /corrections/{correctionId} & POST /restore - 삭제(204) 및 복원(200)")
    void deleteAndRestoreCorrection_success() throws Exception {
        String etag = testCorrection.getEtag();

        // 1. DELETE
        var deleteResult = mockMvc.perform(delete("/api/v1/worksheets/{worksheetId}/corrections/{correctionId}",
                        testWorksheet.getId(), testCorrection.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, etag))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();

        String deleteEtag = deleteResult.getResponse().getHeader(HttpHeaders.ETAG);

        // 2. RESTORE
        mockMvc.perform(post("/api/v1/worksheets/{worksheetId}/corrections/{correctionId}/restore",
                        testWorksheet.getId(), testCorrection.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, deleteEtag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }
}
