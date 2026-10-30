package com.ceos24.autedi.domain;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.comment.dto.request.CommentCreateRequest;
import com.ceos24.autedi.domain.comment.dto.request.CommentUpdateRequest;
import com.ceos24.autedi.domain.comment.entity.Comment;
import com.ceos24.autedi.domain.comment.entity.HighlightType;
import com.ceos24.autedi.domain.comment.repository.CommentRepository;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
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

@SpringBootTest
@Transactional
class CommentControllerTest {

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
    private CommentRepository commentRepository;

    private User testUser;
    private Academy testAcademy;
    private Worksheet testWorksheet;
    private WorksheetPage testPage;
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

        token = "Bearer "
                + jwtTokenProvider.createAccessToken(
                        testUser.getId(),
                        testAcademy.getId(),
                        testUser.getEmail(),
                        testUser.getRole().name());

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

        testWorksheet.updateStatus(WorksheetStatus.READY);

        testPage = worksheetPageRepository.save(WorksheetPage.builder()
                .worksheet(testWorksheet)
                .pageNumber(1)
                .previewImageUrl("http://example.com/preview1.png")
                .width(1000)
                .height(1400)
                .build());
    }

    @Test
    @DisplayName("POST /comments - 코멘트 작성 성공 (201 Created, Location, ETag)")
    void createComment_success() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest(
                testPage.getId(),
                HighlightType.HIGHLIGHT,
                List.of(new RegionPayload(0.1, 0.2, 0.3, 0.05)),
                "이 부분을 더 자세히 설명해 보세요.");

        mockMvc.perform(post("/api/v1/worksheets/{worksheetId}/comments", testWorksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.result.content").value("이 부분을 더 자세히 설명해 보세요."))
                .andExpect(jsonPath("$.result.highlightType").value("HIGHLIGHT"));
    }

    @Test
    @DisplayName("GET /comments & GET /comments/{commentId} - 코멘트 목록 및 단건 조회 성공")
    void getComments_success() throws Exception {
        Comment comment = commentRepository.save(Comment.builder()
                .worksheet(testWorksheet)
                .page(testPage)
                .user(testUser)
                .highlightType(HighlightType.UNDERLINE)
                .regions(List.of(new RegionPayload(0.1, 0.2, 0.3, 0.05)))
                .content("밑줄 코멘트입니다.")
                .build());

        // 목록 조회
        mockMvc.perform(get("/api/v1/worksheets/{worksheetId}/comments", testWorksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(comment.getId()));

        // 단건 조회
        mockMvc.perform(get(
                                "/api/v1/worksheets/{worksheetId}/comments/{commentId}",
                                testWorksheet.getId(),
                                comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.result.content").value("밑줄 코멘트입니다."));
    }

    @Test
    @DisplayName("PATCH /comments/{commentId} - 코멘트 내용 수정 성공")
    void updateComment_success() throws Exception {
        Comment comment = commentRepository.save(Comment.builder()
                .worksheet(testWorksheet)
                .page(testPage)
                .user(testUser)
                .highlightType(HighlightType.HIGHLIGHT)
                .regions(List.of(new RegionPayload(0.1, 0.2, 0.3, 0.05)))
                .content("기존 내용")
                .build());

        String etag = comment.getEtag();
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 코멘트 내용입니다.");

        mockMvc.perform(patch(
                                "/api/v1/worksheets/{worksheetId}/comments/{commentId}",
                                testWorksheet.getId(),
                                comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content").value("수정된 코멘트 내용입니다."));
    }

    @Test
    @DisplayName("DELETE /comments/{commentId} - 코멘트 논리 삭제 성공 (204 No Content 후 조회 시 404)")
    void deleteComment_success() throws Exception {
        Comment comment = commentRepository.save(Comment.builder()
                .worksheet(testWorksheet)
                .page(testPage)
                .user(testUser)
                .highlightType(HighlightType.HIGHLIGHT)
                .regions(List.of(new RegionPayload(0.1, 0.2, 0.3, 0.05)))
                .content("삭제될 내용")
                .build());

        String etag = comment.getEtag();

        // 1. DELETE
        mockMvc.perform(delete(
                                "/api/v1/worksheets/{worksheetId}/comments/{commentId}",
                                testWorksheet.getId(),
                                comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, etag))
                .andExpect(status().isNoContent());

        // 2. 단건 조회 시 404
        mockMvc.perform(get(
                                "/api/v1/worksheets/{worksheetId}/comments/{commentId}",
                                testWorksheet.getId(),
                                comment.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }
}
