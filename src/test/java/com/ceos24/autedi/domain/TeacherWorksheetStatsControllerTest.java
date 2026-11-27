package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.teacher.service.MonthRange;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 선생님 목록의 월별 첨삭 수와 선생님 첨삭 이력 조회를 함께 검증한다. 두 기능은 같은 집계 기준을 쓴다. */
@SpringBootTest
@Transactional
class TeacherWorksheetStatsControllerTest {

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
    private JwtTokenProvider tokens;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private Academy academy;
    private User director;
    private User teacher;
    private User peer;
    private Student kim;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academies.saveAndFlush(Academy.builder().name("집계 기관").build());
        director = user(academy, "director", "박원장", Role.DIRECTOR);
        teacher = user(academy, "teacher", "김선생", Role.TEACHER);
        peer = user(academy, "peer", "이선생", Role.TEACHER);
        kim = student(academy, "김학생");
    }

    // ---------------------------------------------------------------- 월별 첨삭 수

    @Test
    @DisplayName("목록의 월별 첨삭 수는 이번 달 처음 저장한 임시저장·완료만 세고 저장 전·삭제·다른 달·다른 선생님은 제외한다")
    void monthlyCountUsesCurrentMonthAndCountedStatuses() throws Exception {
        MonthRange month = MonthRange.current();
        LocalDateTime inMonth = month.from().plusDays(2);
        sheet(teacher, "TEMP_SAVED", inMonth, "임시저장", kim);
        sheet(teacher, "COMPLETED", inMonth.plusHours(1), "완료", kim);
        sheet(teacher, "UNSAVED", inMonth, "저장 전", kim);
        deleted(sheet(teacher, "TEMP_SAVED", inMonth, "삭제됨", kim));
        sheet(teacher, "COMPLETED", month.from().minusDays(1), "지난달", kim);
        sheet(peer, "COMPLETED", inMonth, "다른 선생님", kim);

        JsonNode items = body(mvc.perform(teacherList())
                        .andExpect(status().isOk())
                        .andReturn())
                .get("items");

        assertThat(monthlyCount(items, teacher)).isEqualTo(2);
        assertThat(monthlyCount(items, peer)).isEqualTo(1);
    }

    @Test
    @DisplayName("월 경계: 이번 달 1일 0시는 포함하고 지난달 마지막 순간과 다음 달 1일 0시는 제외한다")
    void monthBoundaries() throws Exception {
        MonthRange month = MonthRange.current();
        sheet(teacher, "TEMP_SAVED", month.from(), "이번 달 첫 순간", kim);
        sheet(teacher, "TEMP_SAVED", month.to().minusNanos(1_000), "이번 달 마지막 순간", kim);
        sheet(teacher, "TEMP_SAVED", month.from().minusNanos(1_000), "지난달 마지막 순간", kim);
        sheet(teacher, "TEMP_SAVED", month.to(), "다음 달 첫 순간", kim);

        JsonNode items = body(mvc.perform(teacherList())
                        .andExpect(status().isOk())
                        .andReturn())
                .get("items");

        assertThat(monthlyCount(items, teacher)).isEqualTo(2);
    }

    @Test
    @DisplayName("첨삭이 없는 선생님과 퇴사한 선생님의 숫자도 내려준다")
    void zeroAndResignedTeachers() throws Exception {
        MonthRange month = MonthRange.current();
        sheet(peer, "COMPLETED", month.from().plusDays(1), "퇴사 전 작업", kim);
        resign(peer);

        JsonNode items = body(mvc.perform(teacherList())
                        .andExpect(status().isOk())
                        .andReturn())
                .get("items");

        assertThat(monthlyCount(items, teacher)).isZero();
        assertThat(monthlyCount(items, peer)).isEqualTo(1);
    }

    @Test
    @DisplayName("목록의 이번 달 첨삭 수와 이번 달 이력 건수는 항상 같다")
    void listCountMatchesHistoryTotal() throws Exception {
        MonthRange month = MonthRange.current();
        sheet(teacher, "TEMP_SAVED", month.from().plusDays(1), "하나", kim);
        sheet(teacher, "COMPLETED", month.from().plusDays(2), "둘", null);
        sheet(teacher, "UNSAVED", month.from().plusDays(3), "저장 전", kim);
        deleted(sheet(teacher, "COMPLETED", month.from().plusDays(4), "삭제", kim));
        sheet(teacher, "COMPLETED", month.from().minusDays(3), "지난달", kim);

        JsonNode items = body(mvc.perform(teacherList())
                        .andExpect(status().isOk())
                        .andReturn())
                .get("items");
        JsonNode history = body(mvc.perform(history(teacher, "?month=" + YearMonth.now(MonthRange.ZONE)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(history.get("total").asLong()).isEqualTo(monthlyCount(items, teacher));
        assertThat(history.get("total").asLong()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- 첨삭 이력

    @Test
    @DisplayName("이력은 전체 기간의 임시저장·완료를 처음 저장한 시각 최신순으로 보여주고 저장 전·삭제·다른 선생님 학습지는 제외한다")
    void historyListsCountedWorksheetsNewestFirst() throws Exception {
        sheet(teacher, "COMPLETED", at("2026-09-10T10:00:00"), "9월 완료", kim);
        sheet(teacher, "TEMP_SAVED", at("2026-11-05T09:00:00"), "11월 임시", kim);
        sheet(teacher, "COMPLETED", at("2026-10-20T09:00:00"), "10월 완료", kim);
        sheet(teacher, "UNSAVED", at("2026-11-06T09:00:00"), "저장 전", kim);
        deleted(sheet(teacher, "TEMP_SAVED", at("2026-11-07T09:00:00"), "삭제됨", kim));
        sheet(peer, "COMPLETED", at("2026-11-08T09:00:00"), "다른 선생님", kim);

        JsonNode result = body(
                mvc.perform(history(teacher, "")).andExpect(status().isOk()).andReturn());

        List<String> titles = titles(result.get("items"));
        assertThat(titles).containsExactly("11월 임시", "10월 완료", "9월 완료");
        assertThat(result.get("total").asLong()).isEqualTo(3);
        JsonNode first = result.get("items").get(0);
        assertThat(first.get("worksheetId").asLong()).isPositive();
        assertThat(first.get("studentName").asText()).isEqualTo("김학생");
        assertThat(first.get("studentId").asLong()).isEqualTo(kim.getId());
        assertThat(first.get("saveStatus").asText()).isEqualTo("TEMP_SAVED");
        assertThat(first.get("firstSavedAt").isNull()).isFalse();
        assertThat(result.get("items").get(1).get("saveStatus").asText()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("저장 시각이 같으면 학습지 ID가 큰 것이 먼저 나온다")
    void sameTimestampOrdersByIdDescending() throws Exception {
        LocalDateTime same = at("2026-11-05T09:00:00");
        sheet(teacher, "TEMP_SAVED", same, "먼저", kim);
        sheet(teacher, "TEMP_SAVED", same, "나중", kim);

        JsonNode items = body(mvc.perform(history(teacher, ""))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("items");

        assertThat(titles(items)).containsExactly("나중", "먼저");
    }

    @Test
    @DisplayName("월 필터는 그 달 1일 0시부터 다음 달 1일 0시 전까지만 포함한다")
    void monthFilterBoundaries() throws Exception {
        sheet(teacher, "TEMP_SAVED", at("2026-11-01T00:00:00"), "11월 첫 순간", kim);
        sheet(teacher, "TEMP_SAVED", at("2026-11-30T23:59:59"), "11월 마지막 순간", kim);
        sheet(teacher, "TEMP_SAVED", at("2026-10-31T23:59:59"), "10월 마지막 순간", kim);
        sheet(teacher, "TEMP_SAVED", at("2026-12-01T00:00:00"), "12월 첫 순간", kim);

        JsonNode items = body(mvc.perform(history(teacher, "?month=2026-11"))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("items");

        assertThat(titles(items)).containsExactly("11월 마지막 순간", "11월 첫 순간");
    }

    @Test
    @DisplayName("검색어는 제목과 학생 이름을 대소문자 구분 없이 부분 일치로 찾는다")
    void keywordMatchesTitleOrStudentName() throws Exception {
        Student park = student(academy, "박학생");
        sheet(teacher, "TEMP_SAVED", at("2026-11-01T09:00:00"), "English Essay", kim);
        sheet(teacher, "TEMP_SAVED", at("2026-11-02T09:00:00"), "수학 과제", park);
        sheet(teacher, "COMPLETED", at("2026-11-03T09:00:00"), "독해 연습", kim);

        assertThat(historyTitles(teacher, "?keyword=essay")).containsExactly("English Essay");
        assertThat(historyTitles(teacher, "?keyword=박학")).containsExactly("수학 과제");
        assertThat(historyTitles(teacher, "?keyword=김학생")).containsExactly("독해 연습", "English Essay");
        assertThat(historyTitles(teacher, "?keyword=없는검색어")).isEmpty();
    }

    @Test
    @DisplayName("검색어의 %와 _는 글자 그대로 찾는다")
    void keywordSpecialCharactersAreLiteral() throws Exception {
        sheet(teacher, "TEMP_SAVED", at("2026-11-01T09:00:00"), "100% 정답", kim);
        sheet(teacher, "TEMP_SAVED", at("2026-11-02T09:00:00"), "unit_1", kim);
        sheet(teacher, "TEMP_SAVED", at("2026-11-03T09:00:00"), "unit21", kim);

        assertThat(historyTitles(teacher, "?keyword=%")).containsExactly("100% 정답");
        assertThat(historyTitles(teacher, "?keyword=unit_1")).containsExactly("unit_1");
    }

    @Test
    @DisplayName("제목·학생이 없는 학습지도 검색어가 없으면 이력에 나오고 학생 필드는 null이다")
    void worksheetWithoutTitleOrStudentIsListed() throws Exception {
        sheet(teacher, "TEMP_SAVED", at("2026-11-01T09:00:00"), null, null);

        JsonNode items = body(mvc.perform(history(teacher, ""))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("items");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("title").isNull()).isTrue();
        assertThat(items.get(0).get("studentId").isNull()).isTrue();
        assertThat(items.get(0).get("studentName").isNull()).isTrue();
        assertThat(historyTitles(teacher, "?keyword=아무거나")).isEmpty();
    }

    @Test
    @DisplayName("페이지 나누기는 전체 개수와 페이지 수를 함께 준다")
    void paginates() throws Exception {
        for (int day = 1; day <= 5; day++) {
            sheet(teacher, "TEMP_SAVED", at("2026-11-0" + day + "T09:00:00"), "작업 " + day, kim);
        }

        JsonNode result = body(mvc.perform(history(teacher, "?page=1&size=2"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(titles(result.get("items"))).containsExactly("작업 3", "작업 2");
        assertThat(result.get("total").asLong()).isEqualTo(5);
        assertThat(result.get("page").asInt()).isEqualTo(1);
        assertThat(result.get("size").asInt()).isEqualTo(2);
        assertThat(result.get("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("퇴사한 선생님의 이력도 조회할 수 있다")
    void resignedTeacherHistoryIsReadable() throws Exception {
        sheet(peer, "COMPLETED", at("2026-11-01T09:00:00"), "퇴사 전 작업", kim);
        resign(peer);

        assertThat(historyTitles(peer, "")).containsExactly("퇴사 전 작업");
    }

    // ---------------------------------------------------------------- 권한·대상·입력

    @Test
    @DisplayName("이력 조회는 원장만 할 수 있고 인증이 없으면 401이다")
    void onlyDirectorCanReadHistory() throws Exception {
        mvc.perform(get("/api/v1/teachers/{id}/worksheets", teacher.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teachers/{id}/worksheets", teacher.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("다른 기관·없는 선생님은 404이고 원장은 403이다")
    void targetValidation() throws Exception {
        Academy other = academies.saveAndFlush(Academy.builder().name("다른 기관").build());
        User outsider = user(other, "outsider", "외부 선생님", Role.TEACHER);

        mvc.perform(historyOf(outsider.getId(), ""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
        mvc.perform(historyOf(Long.MAX_VALUE, ""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
        mvc.perform(historyOf(director.getId(), ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "?month=2026-13",
                "?month=abc",
                "?month=2026-1",
                "?month=202611",
                "?page=-1",
                "?size=0",
                "?size=101",
                "?page=abc"
            })
    @DisplayName("잘못된 month·page·size는 400이다")
    void invalidInputIsRejected(String query) throws Exception {
        mvc.perform(history(teacher, query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    // ---------------------------------------------------------------- 도우미

    private RequestBuilder teacherList() {
        return get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, token(director));
    }

    private RequestBuilder history(User target, String query) {
        return historyOf(target.getId(), query);
    }

    private RequestBuilder historyOf(Long teacherId, String query) {
        return get("/api/v1/teachers/{id}/worksheets" + query, teacherId)
                .header(HttpHeaders.AUTHORIZATION, token(director));
    }

    private List<String> historyTitles(User target, String query) throws Exception {
        return titles(body(mvc.perform(history(target, query))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("items"));
    }

    private static List<String> titles(JsonNode items) {
        List<String> titles = new ArrayList<>();
        items.forEach(item ->
                titles.add(item.get("title").isNull() ? null : item.get("title").asText()));
        return titles;
    }

    private static long monthlyCount(JsonNode items, User teacher) {
        for (JsonNode item : items) {
            if (item.get("teacherId").asLong() == teacher.getId()) {
                return item.get("monthlyCorrectionCount").asLong();
            }
        }
        throw new AssertionError("목록에 선생님이 없습니다: " + teacher.getId());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray()).get("result");
    }

    private static LocalDateTime at(String value) {
        return LocalDateTime.parse(value);
    }

    /** 학습지를 만들고 업무 저장 상태·처음 저장 시각·제목·학생을 직접 지정한다. 저장 API가 아직 없어 DB를 직접 갱신한다. */
    private Long sheet(User owner, String saveStatus, LocalDateTime firstSavedAt, String title, Student student) {
        Worksheet created = worksheets.saveAndFlush(Worksheet.builder()
                .academy(owner.getAcademy())
                .user(owner)
                .originalFilename("stats.png")
                .fileUrl("/stats.png")
                .fileType(FileType.PNG)
                .fileSize(100L)
                .fileHash("stats-file-hash")
                .build());
        em.flush();
        jdbc.update(
                "UPDATE worksheets SET save_status = ?, first_saved_at = ?, last_saved_at = ?, title = ?, student_id = ? WHERE id = ?",
                saveStatus,
                Timestamp.valueOf(firstSavedAt),
                Timestamp.valueOf(firstSavedAt),
                title,
                student == null ? null : student.getId(),
                created.getId());
        em.clear();
        return created.getId();
    }

    private void deleted(Long worksheetId) {
        em.flush();
        jdbc.update("UPDATE worksheets SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", worksheetId);
        em.clear();
    }

    private void resign(User user) {
        em.flush();
        jdbc.update("UPDATE users SET status = 'RESIGNED' WHERE id = ?", user.getId());
        em.clear();
    }

    private Student student(Academy owner, String name) {
        return students.saveAndFlush(
                Student.builder().academy(owner).name(name).grade("초등 3").build());
    }

    private User user(Academy owner, String suffix, String name, Role role) {
        return users.saveAndFlush(User.builder()
                .academy(owner)
                .email("stats_" + suffix + "@test.com")
                .passwordHash("hashed")
                .name(name)
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
}
