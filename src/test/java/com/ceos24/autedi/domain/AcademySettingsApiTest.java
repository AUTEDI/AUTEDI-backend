package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.*;
import com.ceos24.autedi.domain.academy.service.AcademySettingsService;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.*;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class AcademySettingsApiTest {
    private static final String ROOT = "/api/v1/academies/me";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AcademyRepository academies;

    @Autowired
    private AcademyGradeRepository grades;

    @Autowired
    private AcademyClassRepository classes;

    @Autowired
    private StudentRepository students;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private AcademySettingsService service;

    @Autowired
    private EntityManager em;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private Academy academy;
    private String director;
    private String teacher;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academies.saveAndFlush(Academy.builder().name("설정 테스트 학원").build());
        director = token(Role.DIRECTOR);
        teacher = token(Role.TEACHER);
    }

    private String token(Role role) {
        User user = users.saveAndFlush(User.builder()
                .academy(academy)
                .name(role.name())
                .email(java.util.UUID.randomUUID() + "@settings.test")
                .passwordHash("hash")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
        return "Bearer " + tokens.createAccessToken(user.getId(), academy.getId(), user.getEmail(), role.name());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }

    private JsonNode result(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsByteArray())
                .get("result");
    }

    private JsonNode state() throws Exception {
        return result(call(get(ROOT + "/settings"), teacher, null).andExpect(status().isOk()));
    }

    private long grade() throws Exception {
        long id = state().get("grades").get(0).get("gradeId").asLong();
        call(put(ROOT + "/grades"), director, Map.of("gradeIds", List.of(id))).andExpect(status().isOk());
        return id;
    }

    private long schoolClass(String name) throws Exception {
        return result(call(post(ROOT + "/classes"), director, Map.of("name", name))
                        .andExpect(status().isCreated()))
                .get("classId")
                .asLong();
    }

    private JsonNode student(long gradeId, Long classId) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("name", "김학생");
        body.put("gradeId", gradeId);
        body.put("classId", classId);
        return result(call(post("/api/v1/students"), teacher, body).andExpect(status().isCreated()));
    }

    @Test
    void defaultGradesAreStableAndInitiallyDisabledAndReadableByBothRoles() throws Exception {
        JsonNode first = state();
        assertThat(first.get("grades")).hasSize(9);
        assertThat(first.get("classes")).isEmpty();
        assertThat(first.at("/grades/0/name").asText()).isEqualTo("초1");
        assertThat(first.at("/grades/6/name").asText()).isEqualTo("중1");
        first.get("grades")
                .forEach(g -> assertThat(g.get("enabled").asBoolean()).isFalse());
        assertThat(result(call(get(ROOT + "/settings"), director, null).andExpect(status().isOk())))
                .isEqualTo(first);
        assertThat(state()).isEqualTo(first);
    }

    @Test
    void customGradeIsTrimmedEnabledAndCannotDuplicateDefaultOrCustomName() throws Exception {
        var created = result(
                call(post(ROOT + "/grades"), director, Map.of("name", "  고1  ")).andExpect(status().isCreated()));
        assertThat(created.get("name").asText()).isEqualTo("고1");
        assertThat(created.get("group").asText()).isEqualTo("CUSTOM");
        assertThat(created.get("enabled").asBoolean()).isTrue();
        for (String name : List.of("고1", " 초1 "))
            call(post(ROOT + "/grades"), director, Map.of("name", name))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ACADEMY_005"));
    }

    @Test
    void selectedGradesReplacePreviousSetAndEmptySelectionIsAllowed() throws Exception {
        var options = state().get("grades");
        long first = options.get(0).get("gradeId").asLong(),
                second = options.get(1).get("gradeId").asLong();
        call(put(ROOT + "/grades"), director, Map.of("gradeIds", List.of(first, second)))
                .andExpect(status().isOk());
        var updated = result(call(put(ROOT + "/grades"), director, Map.of("gradeIds", List.of(second)))
                .andExpect(status().isOk()));
        assertThat(updated.at("/grades/0/enabled").asBoolean()).isFalse();
        assertThat(updated.at("/grades/1/enabled").asBoolean()).isTrue();
        var cleared = result(call(put(ROOT + "/grades"), director, Map.of("gradeIds", List.of()))
                .andExpect(status().isOk()));
        cleared.get("grades")
                .forEach(g -> assertThat(g.get("enabled").asBoolean()).isFalse());
    }

    @Test
    void invalidOrForeignGradeSelectionDoesNotPartiallyChangeExistingSelection() throws Exception {
        long selected = grade();
        Academy other = academies.saveAndFlush(Academy.builder().name("다른 학원").build());
        long foreign = service.get(other.getId()).grades().getFirst().gradeId();
        for (long invalid : List.of(foreign, Long.MAX_VALUE)) {
            call(put(ROOT + "/grades"), director, Map.of("gradeIds", List.of(invalid)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ACADEMY_003"));
            assertThat(grades.findById(selected).orElseThrow().isEnabled()).isTrue();
        }
        call(put(ROOT + "/grades"), director, Map.of("gradeIds", List.of(selected, selected)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyDirectorCanWriteAllFiveSettingsOperations() throws Exception {
        long classId = schoolClass("A반");
        call(put(ROOT + "/grades"), teacher, Map.of("gradeIds", List.of())).andExpect(status().isForbidden());
        call(post(ROOT + "/grades"), teacher, Map.of("name", "고1")).andExpect(status().isForbidden());
        call(post(ROOT + "/classes"), teacher, Map.of("name", "B반")).andExpect(status().isForbidden());
        call(patch(ROOT + "/classes/{id}", classId), teacher, Map.of("name", "B반"))
                .andExpect(status().isForbidden());
        call(delete(ROOT + "/classes/{id}", classId), teacher, null).andExpect(status().isForbidden());
        assertThat(state().get("classes")).hasSize(1);
    }

    @Test
    void endpointsRequireAuthenticationAndRejectUnsupportedRoles() throws Exception {
        mvc.perform(get(ROOT + "/settings")).andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT + "/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\"}"))
                .andExpect(status().isUnauthorized());
        String other = "Bearer " + tokens.createAccessToken(Long.MAX_VALUE, academy.getId(), "other@test.com", "OTHER");
        call(get(ROOT + "/settings"), other, null).andExpect(status().isForbidden());
    }

    @Test
    void classNamesAreTrimmedUniqueWithinAcademyAndRenamingToSameNameIsAllowed() throws Exception {
        long a = schoolClass("  A반  "), b = schoolClass("B반");
        call(post(ROOT + "/classes"), director, Map.of("name", " A반 "))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACADEMY_007"));
        call(patch(ROOT + "/classes/{id}", b), director, Map.of("name", "A반")).andExpect(status().isConflict());
        call(patch(ROOT + "/classes/{id}", a), director, Map.of("name", " A반 ")).andExpect(status().isOk());
        assertThat(classes.findById(a).orElseThrow().getName()).isEqualTo("A반");
        Academy other = academies.saveAndFlush(Academy.builder().name("다른 학원").build());
        assertThat(service.createClass(other.getId(), "A반").name()).isEqualTo("A반");
    }

    @Test
    void studentRegistrationUsesIdsAndRenameAppearsInDetailListAndNameFilter() throws Exception {
        long gradeId = grade(), classId = schoolClass("A반");
        JsonNode created = student(gradeId, classId);
        long id = created.get("studentId").asLong();
        assertThat(created.get("gradeId").asLong()).isEqualTo(gradeId);
        assertThat(created.get("classId").asLong()).isEqualTo(classId);
        call(patch(ROOT + "/classes/{id}", classId), director, Map.of("name", "심화반"))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        call(get("/api/v1/students/{id}", id), teacher, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.className").value("심화반"))
                .andExpect(jsonPath("$.result.classId").value(classId));
        call(get("/api/v1/students").param("gradeId", "" + gradeId).param("classId", "" + classId), teacher, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(1))
                .andExpect(jsonPath("$.result.items[0].className").value("심화반"));
        call(get("/api/v1/students").param("className", "심화반"), teacher, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(1));
        call(get("/api/v1/students").param("className", "A반"), teacher, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(0));
    }

    @Test
    void disablingGradePreservesStudentsAndAllowsContactUpdatesAndSameSelectionButBlocksNewAssignments()
            throws Exception {
        long gradeId = grade();
        long studentId = student(gradeId, null).get("studentId").asLong();
        long second = state().at("/grades/1/gradeId").asLong();
        call(put(ROOT + "/grades"), director, Map.of("gradeIds", List.of())).andExpect(status().isOk());
        call(post("/api/v1/students"), teacher, Map.of("name", "새 학생", "gradeId", gradeId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACADEMY_004"));
        call(
                        patch("/api/v1/students/{id}", studentId),
                        teacher,
                        Map.of("guardianPhone", "02-123-4567", "gradeId", gradeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.gradeId").value(gradeId));
        call(patch("/api/v1/students/{id}", studentId), teacher, Map.of("gradeId", second))
                .andExpect(status().isConflict());
        call(get("/api/v1/students").param("gradeId", "" + gradeId), teacher, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(1));
    }

    @Test
    void assignedClassCannotBeDeletedUntilStudentIsUnassigned() throws Exception {
        long classId = schoolClass("A반");
        long id = student(grade(), classId).get("studentId").asLong();
        call(delete(ROOT + "/classes/{id}", classId), director, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACADEMY_008"));
        mvc.perform(patch("/api/v1/students/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.classId").isEmpty())
                .andExpect(jsonPath("$.result.className").isEmpty());
        call(delete(ROOT + "/classes/{id}", classId), director, null).andExpect(status().isNoContent());
        call(delete(ROOT + "/classes/{id}", classId), director, null).andExpect(status().isNotFound());
    }

    @Test
    void foreignAndMissingIdsCannotBeUsedToReadMutateOrAssignSettings() throws Exception {
        long gradeId = grade();
        long studentId = student(gradeId, null).get("studentId").asLong();
        Academy other = academies.saveAndFlush(Academy.builder().name("다른 학원").build());
        long foreignGrade = service.createGrade(other.getId(), "고1").gradeId();
        long foreignClass = service.createClass(other.getId(), "비공개 반").classId();
        for (long id : List.of(foreignClass, Long.MAX_VALUE)) {
            call(patch(ROOT + "/classes/{id}", id), director, Map.of("name", "침범"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ACADEMY_006"));
            call(delete(ROOT + "/classes/{id}", id), director, null).andExpect(status().isNotFound());
            call(post("/api/v1/students"), teacher, Map.of("name", "새 학생", "gradeId", gradeId, "classId", id))
                    .andExpect(status().isNotFound());
            call(patch("/api/v1/students/{id}", studentId), teacher, Map.of("classId", id))
                    .andExpect(status().isNotFound());
        }
        call(post("/api/v1/students"), teacher, Map.of("name", "새 학생", "gradeId", foreignGrade))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACADEMY_003"));
        call(patch("/api/v1/students/{id}", studentId), teacher, Map.of("gradeId", foreignGrade))
                .andExpect(status().isNotFound());
        assertThat(state().get("classes")).isEmpty();
        assertThat(state().get("grades")).hasSize(9);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"name\":\"학생\",\"grade\":\"초1\"}",
                "{\"name\":\"학생\"}",
                "{\"name\":\"학생\",\"gradeId\":null}",
                "{\"name\":\"학생\",\"gradeId\":0}",
                "{\"name\":\"학생\",\"gradeId\":1,\"className\":\"A반\"}",
                "{\"name\":\"학생\",\"gradeId\":1,\"classId\":-1}"
            })
    void registrationRejectsFreeTextMissingAndInvalidIds(String body) throws Exception {
        mvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        assertThat(students.findByAcademyIdAndNameOrderByIdAsc(academy.getId(), "학생"))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void namesMustBeNonblankAndWithinStorageLimit(String path, String name) throws Exception {
        call(post(ROOT + path), director, Map.of("name", name)).andExpect(status().isBadRequest());
    }

    static Stream<Arguments> invalidNames() {
        return Stream.of(
                Arguments.of("/grades", ""),
                Arguments.of("/grades", " \t "),
                Arguments.of("/grades", "가".repeat(51)),
                Arguments.of("/classes", ""),
                Arguments.of("/classes", "  "),
                Arguments.of("/classes", "가".repeat(101)));
    }

    @Test
    void legacyStudentsAreLinkedOnceWithoutLosingLabelsOrChangingOtherAcademies() throws Exception {
        Student first = students.saveAndFlush(Student.builder()
                .academy(academy)
                .name("기존1")
                .grade("기존 학년")
                .className("기존 반")
                .build());
        Student second = students.saveAndFlush(Student.builder()
                .academy(academy)
                .name("기존2")
                .grade("기존 학년")
                .className("기존 반")
                .build());
        Academy other = academies.saveAndFlush(Academy.builder().name("다른 학원").build());
        Student foreign = students.saveAndFlush(Student.builder()
                .academy(other)
                .name("기존3")
                .grade("기존 학년")
                .className("기존 반")
                .build());
        JsonNode configured = state();
        assertThat(configured.get("grades")).hasSize(10);
        assertThat(configured.get("classes")).hasSize(1);
        em.flush();
        em.clear();
        Student migrated = students.findById(first.getId()).orElseThrow();
        assertThat(migrated.getGrade()).isEqualTo("기존 학년");
        assertThat(migrated.getClassName()).isEqualTo("기존 반");
        assertThat(migrated.getGradeId()).isNotNull();
        assertThat(migrated.getClassId())
                .isEqualTo(students.findById(second.getId()).orElseThrow().getClassId());
        assertThat(students.findById(foreign.getId()).orElseThrow().getGradeId())
                .isNull();
        call(put(ROOT + "/grades"), director, Map.of("gradeIds", List.of())).andExpect(status().isOk());
        state().get("grades")
                .forEach(g -> assertThat(g.get("enabled").asBoolean()).isFalse());
        assertThat(state().get("grades")).hasSize(10);
        call(delete(ROOT + "/classes/{id}", migrated.getClassId()), director, null)
                .andExpect(status().isConflict());
    }
}
