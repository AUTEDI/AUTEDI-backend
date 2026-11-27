package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class WorksheetMetadataControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AcademyRepository academies;

    @Autowired
    private UserRepository users;

    @Autowired
    private StudentRepository students;

    @Autowired
    private WorksheetRepository worksheets;

    @Autowired
    private WorksheetPageRepository pages;

    @Autowired
    private WorksheetSettingRepository settings;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private MockMvc mvc;
    private Academy academy;
    private User teacher;
    private User director;
    private User peer;
    private Student student;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academies.saveAndFlush(Academy.builder().name("B4 기관").build());
        teacher = user(academy, "teacher", Role.TEACHER);
        director = user(academy, "director", Role.DIRECTOR);
        peer = user(academy, "peer", Role.TEACHER);
        student = students.saveAndFlush(Student.builder()
                .academy(academy)
                .name("김학생")
                .grade("초등 3")
                .className("오후반")
                .studentPhone("01012345678")
                .guardianPhone("01087654321")
                .build());
    }

    @ParameterizedTest
    @MethodSource("ownerCases")
    @DisplayName("원장·선생님 업로더는 DRAFT·FAILED에서 제목·학생을 지정하고 상세로 다시 조회한다")
    void ownerCanAssignMetadata(Role role, WorksheetStatus state) throws Exception {
        User owner = role == Role.DIRECTOR ? director : teacher;
        Worksheet worksheet = worksheet(owner, state);
        String oldEtag = worksheet.getEtag();
        int oldVersion = worksheet.getVersion();
        MvcResult updated = update(worksheet, owner, "  11월 영어 작문  ", student.getId(), oldEtag);
        JsonNode body = body(updated);
        assertThat(body.get("title").asText()).isEqualTo("11월 영어 작문");
        assertThat(body.at("/student/studentId").asLong()).isEqualTo(student.getId());
        assertThat(body.at("/student/name").asText()).isEqualTo("김학생");
        assertThat(body.at("/student/grade").asText()).isEqualTo("초등 3");
        assertThat(body.at("/student/className").asText()).isEqualTo("오후반");
        assertThat(body.get("student").has("studentPhone")).isFalse();
        assertThat(body.get("student").has("guardianPhone")).isFalse();
        assertThat(body.get("status").asText()).isEqualTo(state.name());
        assertThat(body.get("saveStatus").asText()).isEqualTo("UNSAVED");
        assertThat(body.get("firstSavedAt").isNull()).isTrue();
        assertThat(body.get("lastSavedAt").isNull()).isTrue();
        assertThat(body.get("completedAt").isNull()).isTrue();
        assertThat(body.get("etag").asText()).isNotEqualTo(oldEtag);
        assertThat(updated.getResponse().getHeader(HttpHeaders.ETAG))
                .isEqualTo(body.get("etag").asText());
        assertThat(body.at("/pages/0/pageNumber").asInt()).isEqualTo(1);

        em.clear();
        Worksheet saved = worksheets.findById(worksheet.getId()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("11월 영어 작문");
        assertThat(saved.getStudent().getId()).isEqualTo(student.getId());
        assertThat(saved.getUser().getId()).isEqualTo(owner.getId());
        assertThat(saved.getLastSavedBy()).isNull();
        assertThat(saved.getVersion()).isEqualTo(oldVersion + 1);
        assertThat(saved.getUpdatedAt()).isNotNull();
        mvc.perform(get("/api/v1/worksheets/{id}", worksheet.getId()).header(HttpHeaders.AUTHORIZATION, token(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, body.get("etag").asText()))
                .andExpect(jsonPath("$.result.title").value("11월 영어 작문"))
                .andExpect(jsonPath("$.result.student.studentId").value(student.getId()));
    }

    static Stream<Arguments> ownerCases() {
        return Stream.of(Role.TEACHER, Role.DIRECTOR)
                .flatMap(role -> Stream.of(
                        Arguments.of(role, WorksheetStatus.DRAFT), Arguments.of(role, WorksheetStatus.FAILED)));
    }

    @ParameterizedTest
    @EnumSource(
            value = WorksheetStatus.class,
            names = {"DRAFT", "FAILED"})
    @DisplayName("같은 기관 원장은 다른 교사가 업로드한 학습지도 수정하며 업로더는 유지한다")
    void directorCanEditAnotherUpload(WorksheetStatus state) throws Exception {
        Worksheet worksheet = worksheet(teacher, state);
        update(worksheet, director, "원장이 지정한 제목", student.getId(), null);
        assertThat(worksheet.getTitle()).isEqualTo("원장이 지정한 제목");
        assertThat(worksheet.getUser().getId()).isEqualTo(teacher.getId());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("다른 선생님은 같은 기관이어도 원장·교사의 학습지를 수정하지 못한다")
    void peerTeacherCannotEdit(Role ownerRole) throws Exception {
        Worksheet worksheet = worksheet(ownerRole == Role.DIRECTOR ? director : teacher, WorksheetStatus.DRAFT);
        mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(peer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("변경 금지", student.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        assertThat(worksheet.getTitle()).isNull();
        assertThat(worksheet.getStudent()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = WorksheetStatus.class,
            names = {"PROCESSING", "READY"})
    @DisplayName("AI 처리 중·처리 완료 상태의 제목·학생 수정은 409이다")
    void processingAndReadyRejectMetadata(WorksheetStatus state) throws Exception {
        Worksheet worksheet = worksheet(teacher, state);
        mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("변경 금지", student.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSHEET_002"));
        assertThat(worksheet.getTitle()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = WorksheetStatus.class,
            names = {"PROCESSING", "READY"})
    @DisplayName("엔티티를 직접 호출해도 AI 상태 제한을 우회하지 못하고 기존 메타데이터를 유지한다")
    void entityAlsoProtectsAiState(WorksheetStatus state) {
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        worksheet.updateMetadata("기존 제목", student);
        worksheet.updateStatus(state);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> worksheet.updateMetadata("변경 금지", student))
                .isInstanceOf(IllegalStateException.class);
        assertThat(worksheet.getTitle()).isEqualTo("기존 제목");
    }

    @Test
    @DisplayName("AI가 DRAFT여도 업무 최종 저장 완료 상태는 수정할 수 없다")
    void completedWorksheetIsReadOnly() throws Exception {
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        nativeState(worksheet.getId(), "UPDATE worksheets SET save_status = 'COMPLETED' WHERE id = ?");
        mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(director))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("변경 금지", student.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSHEET_003"));
    }

    @ParameterizedTest
    @EnumSource(
            value = WorksheetStatus.class,
            names = {"DRAFT", "READY"})
    @DisplayName("삭제된 학습지는 수정·상세·검토 경로 모두 404다")
    void deletedWorksheetIsHidden(WorksheetStatus state) throws Exception {
        Worksheet worksheet = worksheet(teacher, state);
        nativeState(worksheet.getId(), "UPDATE worksheets SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?");
        mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(director))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("변경 금지", student.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"));
        mvc.perform(get("/api/v1/worksheets/{id}", worksheet.getId()).header(HttpHeaders.AUTHORIZATION, token(teacher)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/worksheets/{id}/review", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("외부 기관 원장도 타 기관 학습지는 없는 학습지와 동일한 404다")
    void foreignWorksheetIsHidden() throws Exception {
        Academy foreign = academies.saveAndFlush(Academy.builder().name("다른 기관").build());
        User outsider = user(foreign, "foreign", Role.DIRECTOR);
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        MvcResult existing = mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("변경 금지", student.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"))
                .andReturn();
        MvcResult missing = mvc.perform(patch("/api/v1/worksheets/{id}", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, token(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("변경 금지", student.getId())))
                .andExpect(status().isNotFound())
                .andReturn();
        assertThat(json.readTree(existing.getResponse().getContentAsByteArray()))
                .isEqualTo(json.readTree(missing.getResponse().getContentAsByteArray()));
    }

    @Test
    @DisplayName("타 기관 학생과 없는 학생은 같은 404이며 기존 제목·학생을 부분 변경하지 않는다")
    void invalidStudentDoesNotMutateMetadata() throws Exception {
        Academy foreign = academies.saveAndFlush(Academy.builder().name("다른 기관").build());
        Student outsider = students.saveAndFlush(
                Student.builder().academy(foreign).name("외부 학생").grade("초등 4").build());
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        worksheet.updateMetadata("기존 제목", student);
        worksheets.flush();
        for (Long id : new Long[] {outsider.getId(), Long.MAX_VALUE}) {
            mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                            .header(HttpHeaders.AUTHORIZATION, token(teacher))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request("변경 금지", id)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STUDENT_001"));
        }
        assertThat(worksheet.getTitle()).isEqualTo("기존 제목");
        assertThat(worksheet.getStudent().getId()).isEqualTo(student.getId());
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    @DisplayName("제목·학생 필수값과 저장 길이 오류는 400이며 학습지를 변경하지 않는다")
    void invalidInputIsRejected(String title, Object studentId) throws Exception {
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("studentId", studentId);
        mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        assertThat(worksheet.getTitle()).isNull();
        assertThat(worksheet.getStudent()).isNull();
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(null, 1L),
                Arguments.of("", 1L),
                Arguments.of(" \t ", 1L),
                Arguments.of("가".repeat(256), 1L),
                Arguments.of("제목", null),
                Arguments.of("제목", 0L),
                Arguments.of("제목", -1L),
                Arguments.of("제목", "abc"),
                Arguments.of("제목", "9223372036854775808"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "", "null", "{", "[]", "{\"title\":\"제목\"}", "{\"studentId\":1}"})
    @DisplayName("잘못된 JSON 또는 제목·학생 중 하나를 생략한 요청은 400이다")
    void missingPairOrUnreadableBody(String body) throws Exception {
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("제목 앞뒤 공백 제거 후 255자까지 허용한다")
    void maximumTitleLengthAfterStrip() throws Exception {
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        JsonNode body = body(update(worksheet, teacher, "  " + "가".repeat(255) + "  ", student.getId(), null));
        assertThat(body.get("title").asText()).hasSize(255);
    }

    @Test
    @DisplayName("If-Match는 선택이며 지정 시 현재 ETag와 비교하고 응답에 갱신된 버전을 반환한다")
    void optimisticPreconditionAndReturnedEtag() throws Exception {
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        String oldEtag = worksheet.getEtag();
        MvcResult first = update(worksheet, teacher, "첫 제목", student.getId(), "  " + oldEtag + "  ");
        String currentEtag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(currentEtag).isNotEqualTo(oldEtag);
        mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher))
                        .header(HttpHeaders.IF_MATCH, oldEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("변경 금지", student.getId())))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("COMMON_412"));
        assertThat(worksheet.getTitle()).isEqualTo("첫 제목");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "wrong", "\"ws_999999-v1\""})
    @DisplayName("빈 값 또는 불일치하는 If-Match는 412다")
    void invalidIfMatch(String ifMatch) throws Exception {
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher))
                        .header(HttpHeaders.IF_MATCH, ifMatch)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("변경 금지", student.getId())))
                .andExpect(status().isPreconditionFailed());
        assertThat(worksheet.getTitle()).isNull();
    }

    @Test
    @DisplayName("If-Match의 와일드카드를 지원한다")
    void wildcardIfMatch() throws Exception {
        update(worksheet(teacher, WorksheetStatus.DRAFT), teacher, "와일드카드", student.getId(), "*");
    }

    @Test
    @DisplayName("메타데이터 변경은 저장 상태·업무 저장자/시각·파일·영역 설정을 변경하지 않는다")
    void metadataDoesNotPerformBusinessSave() throws Exception {
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.FAILED);
        LocalDateTime savedAt = LocalDateTime.of(2026, 11, 26, 12, 0);
        em.flush();
        jdbc.update(
                "UPDATE worksheets SET save_status = 'TEMP_SAVED', first_saved_at = ?, last_saved_at = ?, last_saved_by = ? WHERE id = ?",
                savedAt,
                savedAt,
                teacher.getId(),
                worksheet.getId());
        em.clear();
        JsonNode body = body(update(worksheet, director, "변경 제목", student.getId(), null));
        assertThat(body.get("saveStatus").asText()).isEqualTo("TEMP_SAVED");
        assertThat(LocalDateTime.parse(body.get("firstSavedAt").asText())).isEqualTo(savedAt);
        assertThat(LocalDateTime.parse(body.get("lastSavedAt").asText())).isEqualTo(savedAt);
        assertThat(body.get("completedAt").isNull()).isTrue();
        Worksheet saved = worksheets.findById(worksheet.getId()).orElseThrow();
        assertThat(saved.getLastSavedBy().getId()).isEqualTo(teacher.getId());
        assertThat(saved.getStatus()).isEqualTo(WorksheetStatus.FAILED);
        assertThat(saved.getFileHash()).isEqualTo("b4-file-hash");
        assertThat(settings.findAllByWorksheetId(saved.getId())).hasSize(1);
    }

    @Test
    @DisplayName("제목·학생 미지정인 기존 상세와 READY 검토도 조회 가능하며 새 필드를 함께 제공한다")
    void legacyAndReviewResponses() throws Exception {
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        mvc.perform(get("/api/v1/worksheets/{id}", worksheet.getId()).header(HttpHeaders.AUTHORIZATION, token(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").isEmpty())
                .andExpect(jsonPath("$.result.student").isEmpty())
                .andExpect(jsonPath("$.result.saveStatus").value("UNSAVED"));
        update(worksheet, teacher, "검토 화면 제목", student.getId(), null);
        worksheet.updateStatus(WorksheetStatus.READY);
        worksheets.flush();
        mvc.perform(get("/api/v1/worksheets/{id}/review", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.worksheet.title").value("검토 화면 제목"))
                .andExpect(jsonPath("$.result.worksheet.student.studentId").value(student.getId()))
                .andExpect(jsonPath("$.result.worksheet.status").value("READY"))
                .andExpect(jsonPath("$.result.worksheet.saveStatus").value("UNSAVED"));
    }

    @Test
    @DisplayName("인증 없는 요청은 401이며 원장·선생님 외 역할은 403이다")
    void authenticationAndRole() throws Exception {
        Worksheet worksheet = worksheet(teacher, WorksheetStatus.DRAFT);
        mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", student.getId())))
                .andExpect(status().isUnauthorized());
        String unsupported =
                "Bearer " + tokens.createAccessToken(teacher.getId(), academy.getId(), teacher.getEmail(), "OTHER");
        mvc.perform(patch("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, unsupported)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", student.getId())))
                .andExpect(status().isForbidden());
    }

    private Worksheet worksheet(User owner, WorksheetStatus status) {
        Worksheet worksheet = worksheets.saveAndFlush(Worksheet.builder()
                .academy(owner.getAcademy())
                .user(owner)
                .originalFilename("b4.png")
                .fileUrl("/b4.png")
                .fileType(FileType.PNG)
                .fileSize(100L)
                .fileHash("b4-file-hash")
                .build());
        worksheet.updateStatus(status);
        pages.saveAndFlush(WorksheetPage.builder()
                .worksheet(worksheet)
                .pageNumber(1)
                .previewImageUrl("/b4-prepared.png")
                .width(1000)
                .height(1400)
                .build());
        settings.saveAndFlush(WorksheetSetting.builder()
                .worksheet(worksheet)
                .area(CorrectionArea.GRAMMAR_SPELLING)
                .level(CorrectionLevel.STANDARD)
                .build());
        return worksheet;
    }

    private User user(Academy owner, String suffix, Role role) {
        return users.saveAndFlush(User.builder()
                .academy(owner)
                .email("b4_" + suffix + "@test.com")
                .passwordHash("hashed")
                .name(suffix)
                .role(role)
                .build());
    }

    private String token(User user) {
        return "Bearer "
                + tokens.createAccessToken(
                        user.getId(),
                        user.getAcademy().getId(),
                        user.getEmail(),
                        user.getRole().name());
    }

    private String request(String title, Long studentId) throws Exception {
        return json.writeValueAsString(Map.of("title", title, "studentId", studentId));
    }

    private MvcResult update(Worksheet worksheet, User editor, String title, Long studentId, String ifMatch)
            throws Exception {
        var request = patch("/api/v1/worksheets/{id}", worksheet.getId())
                .header(HttpHeaders.AUTHORIZATION, token(editor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(title, studentId));
        if (ifMatch != null) request.header(HttpHeaders.IF_MATCH, ifMatch);
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray()).get("result");
    }

    private void nativeState(Long worksheetId, String sql) {
        em.flush();
        jdbc.update(sql, worksheetId);
        em.clear();
    }
}
