package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSavedResult;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSavedResultRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:b7_list;DB_CLOSE_DELAY=-1;MODE=MySQL",
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "logging.level.org.hibernate.stat=OFF",
            "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
        })
@Transactional
class SavedWorksheetListApiTest {
    @Autowired
    WebApplicationContext context;

    @Autowired
    AcademyRepository academies;

    @Autowired
    UserRepository users;

    @Autowired
    StudentRepository students;

    @Autowired
    WorksheetRepository worksheets;

    @Autowired
    WorksheetSavedResultRepository savedResults;

    @Autowired
    WorksheetPageRepository pages;

    @Autowired
    WorksheetGenerationRepository generations;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;

    @Autowired
    private com.ceos24.autedi.domain.academy.service.AcademySettingsService settings;

    private User owner, peer, director, foreign;
    private Student student, otherStudent, foreignStudent;
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 20, 12, 0);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        var academy =
                academies.saveAndFlush(Academy.builder().name("B7 academy").build());
        owner = user(academy, Role.TEACHER);
        peer = user(academy, Role.TEACHER);
        director = user(academy, Role.DIRECTOR);
        foreign = user(academies.saveAndFlush(Academy.builder().name("foreign").build()), Role.TEACHER);
        student = student(academy, "김학생");
        otherStudent = student(academy, "이학생");
        foreignStudent = student(foreign.getAcademy(), "다른 기관 학생");
    }

    @Test
    void listSharesCompletedResultsAndRestrictsDraftsToUploader() throws Exception {
        Long ownDraft = saved("my draft", student, owner, WorksheetSaveStatus.TEMP_SAVED, DAY);
        saved("peer draft", student, peer, WorksheetSaveStatus.TEMP_SAVED, DAY);
        saved("director draft", student, director, WorksheetSaveStatus.TEMP_SAVED, DAY);
        Long ownCompleted = saved("my result", student, owner, WorksheetSaveStatus.COMPLETED, DAY.plusDays(1));
        Long peerCompleted = saved("peer result", student, peer, WorksheetSaveStatus.COMPLETED, DAY.plusDays(2));
        saved("other student", otherStudent, owner, WorksheetSaveStatus.COMPLETED, DAY.plusDays(5));
        saved("foreign result", foreignStudent, foreign, WorksheetSaveStatus.COMPLETED, DAY.plusDays(6));
        var response = result(list(owner));
        assertThat(ids(response)).containsExactly(peerCompleted, ownCompleted, ownDraft);
        assertThat(response.path("total").asLong()).isEqualTo(3);
        assertThat(response.at("/items/0/teacher/teacherId").asLong()).isEqualTo(peer.getId());
        assertThat(response.at("/items/0/canView").asBoolean()).isTrue();
        assertThat(response.at("/items/0/canResume").asBoolean()).isFalse();
        assertThat(response.at("/items/2/canResume").asBoolean()).isTrue();
        assertThat(response.at("/items/0/student/studentId").asLong()).isEqualTo(student.getId());
        assertThat(response.at("/items/0/student").has("guardianPhone")).isFalse();
        assertThat(response.at("/items/0/teacher").has("email")).isFalse();
        assertThat(response.at("/items/0").has("snapshot")).isFalse();
        assertThat(result(list(director)).path("total").asLong()).isEqualTo(3);
    }

    @Test
    void excludesUnsavedDeletedMissingSnapshotAndMissingSaveDate() throws Exception {
        Long visible = saved("visible", student, owner, WorksheetSaveStatus.TEMP_SAVED, DAY);
        saved("unsaved", student, owner, WorksheetSaveStatus.UNSAVED, DAY);
        Long deleted = saved("deleted", student, owner, WorksheetSaveStatus.TEMP_SAVED, DAY);
        Long missingSnapshot = saved("no snapshot", student, owner, WorksheetSaveStatus.COMPLETED, DAY);
        Long missingDate = saved("no date", student, owner, WorksheetSaveStatus.COMPLETED, DAY);
        jdbc.update("update worksheets set deleted_at=current_timestamp where id=?", deleted);
        jdbc.update("delete from worksheet_saved_results where worksheet_id=?", missingSnapshot);
        jdbc.update("update worksheets set last_saved_at=null where id=?", missingDate);
        em.clear();
        assertThat(ids(result(list(owner)))).containsExactly(visible);
    }

    @Test
    void foreignStudentAndMissingStudentAreHiddenAndAnonymousIsRejected() throws Exception {
        call(get(studentPath(foreignStudent.getId())), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"));
        call(get(studentPath(Long.MAX_VALUE)), owner).andExpect(status().isNotFound());
        call(get(studentPath(student.getId())), foreign).andExpect(status().isNotFound());
        mvc.perform(get(studentPath(student.getId()))).andExpect(status().isUnauthorized());
    }

    @Test
    void existingStudentWithNoResultsReturnsEmptyPage() throws Exception {
        var response = result(list(owner));
        assertThat(response.path("items").size()).isZero();
        assertThat(response.path("total").asLong()).isZero();
        assertThat(response.path("page").asInt()).isZero();
        assertThat(response.path("size").asInt()).isEqualTo(20);
        assertThat(response.path("totalPages").asInt()).isZero();
        assertThat(response.path("hasNext").asBoolean()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"%,1", "_,2", "!,1", "ESSAY,2", "100%,1"})
    void keywordSearchTreatsWildcardsLiterallyAndIgnoresAsciiCase(String keyword, int count) throws Exception {
        for (String title : List.of(
                "100% correct",
                "1000 correct", "unit_one", "unitXone", "nice!work", "nice_work", "Essay ABC", "essay def")) {
            saved(title, student, owner, WorksheetSaveStatus.COMPLETED, DAY);
        }
        assertThat(result(call(get(studentPath(student.getId())).param("keyword", "  " + keyword + "  "), owner)
                                .andExpect(status().isOk()))
                        .path("total")
                        .asLong())
                .isEqualTo(count);
    }

    @Test
    void dateRangeIncludesBothWholeDaysWithIndependentBounds() throws Exception {
        saved(
                "before",
                student,
                owner,
                WorksheetSaveStatus.COMPLETED,
                DAY.toLocalDate().atStartOfDay().minusNanos(1000));
        saved(
                "start",
                student,
                owner,
                WorksheetSaveStatus.COMPLETED,
                DAY.toLocalDate().atStartOfDay());
        saved(
                "end",
                student,
                owner,
                WorksheetSaveStatus.COMPLETED,
                DAY.toLocalDate().atTime(23, 59, 59, 999_999_000));
        saved(
                "after",
                student,
                owner,
                WorksheetSaveStatus.COMPLETED,
                DAY.toLocalDate().plusDays(1).atStartOfDay());
        String date = DAY.toLocalDate().toString();
        assertThat(result(call(
                                        get(studentPath(student.getId()))
                                                .param("from", date)
                                                .param("to", date),
                                        owner)
                                .andExpect(status().isOk()))
                        .path("total")
                        .asLong())
                .isEqualTo(2);
        assertThat(result(call(get(studentPath(student.getId())).param("from", date), owner)
                                .andExpect(status().isOk()))
                        .path("total")
                        .asLong())
                .isEqualTo(3);
        assertThat(result(call(get(studentPath(student.getId())).param("to", date), owner)
                                .andExpect(status().isOk()))
                        .path("total")
                        .asLong())
                .isEqualTo(3);
    }

    @Test
    void stableOrderUsesSavedAtAndIdEvenAfterAdditionalEdits() throws Exception {
        Long old = saved("old save, recent edit", student, owner, WorksheetSaveStatus.TEMP_SAVED, DAY.minusDays(1));
        Long first = saved("same save time", student, owner, WorksheetSaveStatus.COMPLETED, DAY);
        Long second = saved("same save time newer id", student, peer, WorksheetSaveStatus.COMPLETED, DAY);
        jdbc.update("update worksheets set updated_at=? where id=?", DAY.plusDays(3), old);
        em.clear();
        assertThat(ids(result(list(owner)))).containsExactly(second, first, old);
    }

    @Test
    void paginationCountUsesSameVisibilityAndFiltersAsItems() throws Exception {
        saved("match 1", student, owner, WorksheetSaveStatus.TEMP_SAVED, DAY);
        saved("match 2", student, owner, WorksheetSaveStatus.COMPLETED, DAY);
        saved("match 3", student, peer, WorksheetSaveStatus.COMPLETED, DAY);
        saved("match hidden", student, peer, WorksheetSaveStatus.TEMP_SAVED, DAY);
        saved("different", student, owner, WorksheetSaveStatus.COMPLETED, DAY);
        var response = result(call(
                        get(studentPath(student.getId()))
                                .param("keyword", "match")
                                .param("page", "1")
                                .param("size", "2"),
                        owner)
                .andExpect(status().isOk()));
        assertThat(response.path("items").size()).isEqualTo(1);
        assertThat(response.path("total").asLong()).isEqualTo(3);
        assertThat(response.path("totalPages").asInt()).isEqualTo(2);
        assertThat(response.path("hasNext").asBoolean()).isFalse();
        var empty = result(call(get(studentPath(student.getId())).param("page", "99"), owner)
                .andExpect(status().isOk()));
        assertThat(empty.path("items").size()).isZero();
        assertThat(empty.path("total").asLong()).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({
        "page,-1",
        "page,x",
        "size,0",
        "size,101",
        "size,x",
        "from,2026-02-30",
        "to,not-a-date",
        "from,0000-01-01",
        "to,+10000-01-01"
    })
    void invalidSearchParametersReturn400(String key, String value) throws Exception {
        call(get(studentPath(student.getId())).param(key, value), owner).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsReversedRangeOverflowOffsetAndOversizedKeyword() throws Exception {
        call(get(studentPath(student.getId())).param("from", "2026-11-21").param("to", "2026-11-20"), owner)
                .andExpect(status().isBadRequest());
        call(
                        get(studentPath(student.getId()))
                                .param("page", Integer.toString(Integer.MAX_VALUE))
                                .param("size", "100"),
                        owner)
                .andExpect(status().isBadRequest());
        call(get(studentPath(student.getId())).param("keyword", "a".repeat(256)), owner)
                .andExpect(status().isBadRequest());
        call(get(studentPath(student.getId())).param("to", "9999-12-31"), owner).andExpect(status().isOk());
    }

    @Test
    void listDoesNotLoadWorksheetEntitiesOrSavedJson() throws Exception {
        for (int i = 0; i < 5; i++) saved("corrupt snapshot " + i, student, owner, WorksheetSaveStatus.COMPLETED, DAY);
        em.clear();
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        assertThat(result(list(owner)).path("items").size()).isEqualTo(5);
        assertThat(stats.getEntityStatistics(WorksheetSavedResult.class.getName())
                        .getLoadCount())
                .isZero();
        assertThat(stats.getEntityStatistics(Worksheet.class.getName()).getLoadCount())
                .isZero();
        stats.clear();
        assertThat(result(home(owner)).path("items").size()).isEqualTo(5);
        assertThat(stats.getEntityStatistics(WorksheetSavedResult.class.getName())
                        .getLoadCount())
                .isZero();
        assertThat(stats.getEntityStatistics(Worksheet.class.getName()).getLoadCount())
                .isZero();
    }

    @Test
    void listCanOpenRealSharedCompletedSnapshotAndKeepsDraftPrivate() throws Exception {
        Long id = work("real result", student, owner);
        var worksheet = worksheets.findById(id).orElseThrow();
        pages.saveAndFlush(WorksheetPage.builder()
                .worksheet(worksheet)
                .pageNumber(1)
                .previewImageUrl("/b7-preview.png")
                .width(1000)
                .height(1400)
                .build());
        var gen = generations.saveAndFlush(WorksheetGeneration.builder()
                .worksheet(worksheet)
                .requestedBy(owner)
                .idempotencyKey(UUID.randomUUID().toString())
                .requestFileHash(worksheet.getFileHash())
                .requestSettingsSnapshot(Map.of())
                .build());
        gen.succeed();
        em.flush();
        String path = "/api/v1/worksheets/" + id;
        call(post(path + "/complete").header(HttpHeaders.IF_MATCH, worksheet.getEtag()), owner)
                .andExpect(status().isOk());
        assertThat(ids(result(list(peer)))).containsExactly(id);
        call(get(path + "/saved-result"), peer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.review.worksheet.saveStatus").value("COMPLETED"));
        call(get(path + "/saved-result"), director).andExpect(status().isOk());
        call(get(path + "/saved-result"), foreign).andExpect(status().isNotFound());
        call(
                        post(path + "/complete")
                                .header(
                                        HttpHeaders.IF_MATCH,
                                        worksheets.findById(id).orElseThrow().getEtag()),
                        peer)
                .andExpect(status().isForbidden());
        Long draft = saved("private draft", student, owner, WorksheetSaveStatus.TEMP_SAVED, DAY);
        call(get("/api/v1/worksheets/" + draft + "/saved-result"), peer).andExpect(status().isForbidden());
        assertThat(ids(result(list(peer)))).containsExactly(id);
    }

    @Test
    void nonReadySavedRowsDisableViewAndResume() throws Exception {
        Long id = saved("processing", student, owner, WorksheetSaveStatus.TEMP_SAVED, DAY);
        jdbc.update("update worksheets set status='PROCESSING' where id=?", id);
        em.clear();
        var row = result(list(owner)).path("items").get(0);
        assertThat(row.path("canView").asBoolean()).isFalse();
        assertThat(row.path("canResume").asBoolean()).isFalse();
    }

    @Test
    void completedResultsKeepTeacherNameAfterUploaderResigns() throws Exception {
        Long id = saved("former teacher result", student, peer, WorksheetSaveStatus.COMPLETED, DAY);
        jdbc.update("update users set status='RESIGNED' where id=?", peer.getId());
        em.clear();
        var response = result(list(director));
        assertThat(ids(response)).containsExactly(id);
        assertThat(response.at("/items/0/teacher/teacherId").asLong()).isEqualTo(peer.getId());
        assertThat(response.at("/items/0/teacher/name").asText()).isEqualTo(peer.getName());
    }

    @Test
    void homeIncludesStudentsAcrossAcademyAndUsesLatestWorkTime() throws Exception {
        Long editedDraft =
                saved("old save recently edited", student, owner, WorksheetSaveStatus.TEMP_SAVED, DAY.minusDays(3));
        Long earlierId =
                saved("other student result", otherStudent, peer, WorksheetSaveStatus.COMPLETED, DAY.minusDays(1));
        Long laterId =
                saved("same work time larger id", student, owner, WorksheetSaveStatus.COMPLETED, DAY.minusDays(1));
        saved("hidden peer draft", student, peer, WorksheetSaveStatus.TEMP_SAVED, DAY.plusDays(1));
        saved("foreign result", foreignStudent, foreign, WorksheetSaveStatus.COMPLETED, DAY.plusDays(2));
        jdbc.update("update worksheets set updated_at=? where id=?", DAY, editedDraft);
        em.clear();
        var response = result(home(owner));
        assertThat(ids(response)).containsExactly(editedDraft, laterId, earlierId);
        assertThat(response.path("total").asLong()).isEqualTo(3);
        assertThat(response.at("/items/0/savedAt").asText())
                .startsWith(DAY.minusDays(3).toLocalDate().toString());
        assertThat(response.at("/items/0/lastWorkedAt").asText())
                .startsWith(DAY.toLocalDate().toString());
        assertThat(response.at("/items/2/student/studentId").asLong()).isEqualTo(otherStudent.getId());
        assertThat(result(home(director)).path("total").asLong()).isEqualTo(2);
    }

    @Test
    void homeExcludesUnsavedDeletedAndMissingSnapshotsAndPaginatesVisibleRows() throws Exception {
        saved("first", student, owner, WorksheetSaveStatus.TEMP_SAVED, DAY);
        saved("second", otherStudent, peer, WorksheetSaveStatus.COMPLETED, DAY);
        saved("third", student, owner, WorksheetSaveStatus.COMPLETED, DAY);
        saved("unsaved", student, owner, WorksheetSaveStatus.UNSAVED, DAY);
        saved("hidden draft", student, peer, WorksheetSaveStatus.TEMP_SAVED, DAY);
        Long deleted = saved("deleted", student, owner, WorksheetSaveStatus.TEMP_SAVED, DAY);
        Long missing = saved("missing snapshot", student, owner, WorksheetSaveStatus.COMPLETED, DAY);
        jdbc.update("update worksheets set deleted_at=current_timestamp where id=?", deleted);
        jdbc.update("delete from worksheet_saved_results where worksheet_id=?", missing);
        em.clear();
        var response =
                result(call(get("/api/v1/worksheets/recent").param("page", "1").param("size", "2"), owner)
                        .andExpect(status().isOk()));
        assertThat(response.path("items").size()).isEqualTo(1);
        assertThat(response.path("total").asLong()).isEqualTo(3);
        assertThat(response.path("totalPages").asInt()).isEqualTo(2);
        assertThat(response.path("hasNext").asBoolean()).isFalse();
    }

    @Test
    void homeReturnsEmptyPageAndRequiresAuthentication() throws Exception {
        var response = result(home(owner));
        assertThat(response.path("items").size()).isZero();
        assertThat(response.path("total").asLong()).isZero();
        assertThat(response.path("size").asInt()).isEqualTo(20);
        mvc.perform(get("/api/v1/worksheets/recent")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,x", "size,0", "size,101", "size,x"})
    void homeRejectsInvalidPagination(String key, String value) throws Exception {
        call(get("/api/v1/worksheets/recent").param(key, value), owner).andExpect(status().isBadRequest());
        call(
                        get("/api/v1/worksheets/recent")
                                .param("page", Integer.toString(Integer.MAX_VALUE))
                                .param("size", "100"),
                        owner)
                .andExpect(status().isBadRequest());
    }

    @Test
    void classRenameUpdatesLiveResultListWithoutRewritingSavedSnapshot() throws Exception {
        Long id = saved("saved", student, owner, WorksheetSaveStatus.COMPLETED, DAY);
        Long academyId = owner.getAcademy().getId();
        var configured = settings.get(academyId);
        Long classId = configured.classes().getFirst().classId();
        var before = savedResults.findById(id).orElseThrow().getSnapshot();
        settings.renameClass(academyId, classId, "새 반 이름");
        em.flush();
        em.clear();
        var row = result(list(owner)).at("/items/0/student");
        assertThat(row.get("classId").asLong()).isEqualTo(classId);
        assertThat(row.get("className").asText()).isEqualTo("새 반 이름");
        assertThat(savedResults.findById(id).orElseThrow().getSnapshot()).isEqualTo(before);
    }

    private Long saved(String title, Student target, User uploader, WorksheetSaveStatus state, LocalDateTime date) {
        Long id = work(title, target, uploader);
        var worksheet = worksheets.findById(id).orElseThrow();
        if (state == WorksheetSaveStatus.UNSAVED) return id;
        if (state == WorksheetSaveStatus.COMPLETED) worksheet.complete(uploader, date);
        else worksheet.saveDraft(uploader, date);
        em.flush();
        var saved = new WorksheetSavedResult(worksheet);
        // 공개 review가 없는 JSON으로 목록이 저장본을 복원·디코딩하지 않는지도 검증한다.
        saved.replace(null, uploader, date, Map.of("listOnlyFixture", true));
        savedResults.saveAndFlush(saved);
        jdbc.update("update worksheets set updated_at=? where id=?", date, id);
        em.clear();
        return id;
    }

    private Long work(String title, Student target, User uploader) {
        var worksheet = worksheets.saveAndFlush(Worksheet.builder()
                .academy(uploader.getAcademy())
                .user(uploader)
                .originalFilename("b7.png")
                .fileUrl("/b7.png")
                .fileType(FileType.PNG)
                .fileSize(100L)
                .fileHash(UUID.randomUUID().toString())
                .build());
        worksheet.updateMetadata(title, target);
        worksheet.updateStatus(WorksheetStatus.READY);
        em.flush();
        return worksheet.getId();
    }

    private User user(Academy academy, Role role) {
        return users.saveAndFlush(User.builder()
                .academy(academy)
                .name(role.name())
                .email(UUID.randomUUID() + "@test.com")
                .passwordHash("hash")
                .role(role)
                .build());
    }

    private Student student(Academy academy, String name) {
        return students.saveAndFlush(Student.builder()
                .academy(academy)
                .name(name)
                .grade("초3")
                .className("오후반")
                .guardianPhone("01012345678")
                .build());
    }

    private String studentPath(Long id) {
        return "/api/v1/students/" + id + "/worksheets";
    }

    private ResultActions list(User user) throws Exception {
        return call(get(studentPath(student.getId())), user).andExpect(status().isOk());
    }

    private ResultActions home(User user) throws Exception {
        return call(get("/api/v1/worksheets/recent"), user).andExpect(status().isOk());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, User user) throws Exception {
        return mvc.perform(request.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer "
                        + tokens.createAccessToken(
                                user.getId(),
                                user.getAcademy().getId(),
                                user.getEmail(),
                                user.getRole().name())));
    }

    private JsonNode result(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsByteArray())
                .get("result");
    }

    private List<Long> ids(JsonNode response) {
        var ids = new java.util.ArrayList<Long>();
        response.path("items").forEach(row -> ids.add(row.path("worksheetId").asLong()));
        return ids;
    }
}
