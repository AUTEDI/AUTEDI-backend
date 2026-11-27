package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 학습지 삭제·완료 공통 접근 정책을 모든 학습지 API에 걸쳐 검증한다. 삭제된 학습지는 모든 API에서 404, 완료된 학습지는 쓰기 API에서 409
 * (WORKSHEET_003)이고 읽기는 허용되어야 한다. 정책은 요청 본문·하위 자원 검증보다 먼저 적용되므로 하위 자원 ID는 존재하지 않아도 된다.
 */
@SpringBootTest
@Transactional
class WorksheetAccessPolicyTest {

    private static final String JSON_REGION = "{\"x\":0.1,\"y\":0.1,\"width\":0.2,\"height\":0.2}";

    /** 학습지 ID를 받아 요청을 만드는 API 한 개. 표시 이름이 테스트 이름에 나온다. */
    record Api(String name, Function<Long, RequestBuilder> request) {
        @Override
        public String toString() {
            return name;
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AcademyRepository academies;

    @Autowired
    private UserRepository users;

    @Autowired
    private WorksheetRepository worksheets;

    @Autowired
    private WorksheetPageRepository pages;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private User owner;
    private Worksheet worksheet;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        Academy academy = academies.saveAndFlush(Academy.builder().name("정책 기관").build());
        owner = users.saveAndFlush(User.builder()
                .academy(academy)
                .email("policy_owner@test.com")
                .passwordHash("hashed")
                .name("owner")
                .role(Role.TEACHER)
                .build());
        Worksheet draft = Worksheet.builder()
                .academy(academy)
                .user(owner)
                .originalFilename("policy.png")
                .fileUrl("/policy.png")
                .fileType(FileType.PNG)
                .fileSize(100L)
                .fileHash("policy-file-hash")
                .build();
        draft.updateStatus(WorksheetStatus.READY);
        // @Version이 미리 채워져 save가 merge로 동작하므로 반환된 영속 엔티티를 써야 한다.
        worksheet = worksheets.saveAndFlush(draft);
        pages.saveAndFlush(WorksheetPage.builder()
                .worksheet(worksheet)
                .pageNumber(1)
                .previewImageUrl("/policy-prepared.png")
                .width(1000)
                .height(1400)
                .build());
    }

    @ParameterizedTest(name = "삭제된 학습지: {0} -> 404")
    @MethodSource("allApis")
    @DisplayName("삭제된 학습지는 읽기·쓰기 모든 API에서 존재하지 않는 것으로 숨긴다")
    void deletedWorksheetIsHiddenEverywhere(Api api) throws Exception {
        changeWorksheet("UPDATE worksheets SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?");

        MvcResult result = perform(api);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(code(result)).isEqualTo("WORKSHEET_001");
    }

    @ParameterizedTest(name = "완료된 학습지: {0} -> 409")
    @MethodSource("writeApis")
    @DisplayName("완료된 학습지는 모든 쓰기 API에서 WORKSHEET_003으로 거부한다")
    void completedWorksheetRejectsWrites(Api api) throws Exception {
        changeWorksheet("UPDATE worksheets SET save_status = 'COMPLETED' WHERE id = ?");

        MvcResult result = perform(api);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(code(result)).isEqualTo("WORKSHEET_003");
    }

    @ParameterizedTest(name = "완료된 학습지: {0} -> 읽기 허용")
    @MethodSource("readApis")
    @DisplayName("완료된 학습지도 읽기 API는 막지 않는다")
    void completedWorksheetAllowsReads(Api api) throws Exception {
        changeWorksheet("UPDATE worksheets SET save_status = 'COMPLETED' WHERE id = ?");

        MvcResult result = perform(api);

        // 완료·삭제 정책이 아닌 다른 이유(없는 생성 작업 등)로 실패할 수는 있지만 정책 때문에 막혀서는 안 된다.
        assertThat(code(result)).isNotIn("WORKSHEET_001", "WORKSHEET_003");
    }

    static Stream<Api> allApis() {
        return Stream.concat(Stream.concat(readApis(), storageReadApis()), writeApis());
    }

    /** 외부 저장소를 읽지 않는 읽기 API. */
    static Stream<Api> readApis() {
        return Stream.of(
                new Api("GET /worksheets/{id}", id -> request(HttpMethod.GET, "/api/v1/worksheets/{id}", id)),
                new Api(
                        "GET /worksheets/{id}/settings",
                        id -> request(HttpMethod.GET, "/api/v1/worksheets/{id}/settings", id)),
                new Api(
                        "GET /worksheets/{id}/review",
                        id -> request(HttpMethod.GET, "/api/v1/worksheets/{id}/review", id)),
                new Api(
                        "GET /worksheets/{id}/corrections",
                        id -> request(HttpMethod.GET, "/api/v1/worksheets/{id}/corrections", id)),
                new Api(
                        "GET /worksheets/{id}/corrections/{correctionId}",
                        id -> request(HttpMethod.GET, "/api/v1/worksheets/{id}/corrections/1", id)),
                new Api(
                        "GET /worksheets/{id}/comments",
                        id -> request(HttpMethod.GET, "/api/v1/worksheets/{id}/comments", id)),
                new Api(
                        "GET /worksheets/{id}/comments/{commentId}",
                        id -> request(HttpMethod.GET, "/api/v1/worksheets/{id}/comments/1", id)),
                new Api(
                        "GET /worksheets/{id}/generations/{generationId}",
                        id -> request(HttpMethod.GET, "/api/v1/worksheets/{id}/generations/1", id)));
    }

    /** 파일 저장소를 읽는 읽기 API. 삭제 검사만 확인한다. */
    static Stream<Api> storageReadApis() {
        return Stream.of(
                new Api("GET /worksheets/{id}/file", id -> request(HttpMethod.GET, "/api/v1/worksheets/{id}/file", id)),
                new Api(
                        "GET /worksheets/{id}/pages/1/image",
                        id -> request(HttpMethod.GET, "/api/v1/worksheets/{id}/pages/1/image", id)));
    }

    static Stream<Api> writeApis() {
        return Stream.of(
                new Api(
                        "PATCH /worksheets/{id} (제목·학생)",
                        id -> jsonRequest(
                                HttpMethod.PATCH, "/api/v1/worksheets/{id}", id, "{\"title\":\"제목\",\"studentId\":1}")),
                new Api("PUT /worksheets/{id}/file", id -> multipart(HttpMethod.PUT, "/api/v1/worksheets/{id}/file", id)
                        .file(new MockMultipartFile("file", "new.png", "image/png", new byte[] {1}))),
                new Api(
                        "PUT /worksheets/{id}/settings",
                        id -> jsonRequest(
                                HttpMethod.PUT,
                                "/api/v1/worksheets/{id}/settings",
                                id,
                                "{\"areas\":[{\"area\":\"GRAMMAR_SPELLING\",\"level\":\"STANDARD\"}]}")),
                new Api(
                        "PATCH /worksheets/{id}/corrections/{correctionId}/apply",
                        id -> request(HttpMethod.PATCH, "/api/v1/worksheets/{id}/corrections/1/apply", id)),
                new Api(
                        "PATCH /worksheets/{id}/corrections/{correctionId}",
                        id -> jsonRequest(
                                HttpMethod.PATCH, "/api/v1/worksheets/{id}/corrections/1", id, "{\"content\":\"수정\"}")),
                new Api(
                        "DELETE /worksheets/{id}/corrections/{correctionId}",
                        id -> request(HttpMethod.DELETE, "/api/v1/worksheets/{id}/corrections/1", id)),
                new Api(
                        "POST /worksheets/{id}/corrections/{correctionId}/restore",
                        id -> request(HttpMethod.POST, "/api/v1/worksheets/{id}/corrections/1/restore", id)),
                new Api(
                        "POST /worksheets/{id}/comments",
                        id -> jsonRequest(
                                HttpMethod.POST,
                                "/api/v1/worksheets/{id}/comments",
                                id,
                                "{\"pageId\":1,\"highlightType\":\"HIGHLIGHT\",\"regions\":[" + JSON_REGION
                                        + "],\"content\":\"코멘트\"}")),
                new Api(
                        "PATCH /worksheets/{id}/comments/{commentId}",
                        id -> jsonRequest(
                                HttpMethod.PATCH, "/api/v1/worksheets/{id}/comments/1", id, "{\"content\":\"수정\"}")),
                new Api(
                        "DELETE /worksheets/{id}/comments/{commentId}",
                        id -> request(HttpMethod.DELETE, "/api/v1/worksheets/{id}/comments/1", id)),
                new Api(
                        "POST /worksheets/{id}/generations",
                        id -> request(HttpMethod.POST, "/api/v1/worksheets/{id}/generations", id)),
                new Api(
                        "POST /worksheets/{id}/generations/{generationId}/retry",
                        id -> request(HttpMethod.POST, "/api/v1/worksheets/{id}/generations/1/retry", id)));
    }

    private static RequestBuilder jsonRequest(HttpMethod method, String url, Long id, String body) {
        return request(method, url, id).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MvcResult perform(Api api) throws Exception {
        RequestBuilder built = api.request().apply(worksheet.getId());
        String authorization = token(owner);
        // 업로드 요청 빌더는 일반 요청 빌더와 타입이 달라 인증 헤더를 요청을 만든 뒤에 붙인다.
        return mvc.perform(servletContext -> {
                    var servletRequest = built.buildRequest(servletContext);
                    servletRequest.addHeader(HttpHeaders.AUTHORIZATION, authorization);
                    return servletRequest;
                })
                .andReturn();
    }

    private String code(MvcResult result) throws Exception {
        byte[] content = result.getResponse().getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }
        JsonNode body;
        try {
            body = json.readTree(content);
        } catch (Exception e) {
            return "";
        }
        return body.path("code").asText("");
    }

    private void changeWorksheet(String sql) {
        em.flush();
        jdbc.update(sql, worksheet.getId());
        em.clear();
    }

    private String token(User user) {
        return "Bearer "
                + tokens.createAccessToken(
                        user.getId(),
                        user.getAcademy().getId(),
                        user.getEmail(),
                        user.getRole().name());
    }
}
