package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class StudentManagementControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AcademyRepository academies;

    @Autowired
    private UserRepository users;

    @Autowired
    private StudentRepository students;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private MockMvc mvc;
    private Academy academy;
    private String teacherToken;
    private String directorToken;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academies.saveAndFlush(Academy.builder().name("학생 관리 테스트 기관").build());
        teacherToken = token(Role.TEACHER);
        directorToken = token(Role.DIRECTOR);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("목록은 로그인 기관의 학생만 이름·ID 순으로 반환하고 연락처를 노출하지 않는다")
    void listIsScopedOrderedAndContainsSummaryOnly(Role role) throws Exception {
        Student charlie = student(academy, "Charlie", "초등 3", "오후반");
        Student alice1 = student(academy, "Alice", "초등 4", null);
        Student alice2 = student(academy, "Alice", "초등 5", "오전반");
        Academy foreign = academies.saveAndFlush(Academy.builder().name("다른 기관").build());
        student(foreign, "Aaron", "초등 3", "오후반");

        mvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, tokenOf(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(3))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.items[0].studentId").value(alice1.getId()))
                .andExpect(jsonPath("$.result.items[1].studentId").value(alice2.getId()))
                .andExpect(jsonPath("$.result.items[2].studentId").value(charlie.getId()))
                .andExpect(jsonPath("$.result.items[0].className").isEmpty())
                .andExpect(jsonPath("$.result.items[0].studentPhone").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].guardianPhone").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].academyId").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("searchCases")
    @DisplayName("이름 부분 검색과 학년·반 일치 필터를 AND로 적용하고 앞뒤 공백을 정리한다")
    void combinedSearchFilters(Map<String, String> query, List<String> expectedNames) throws Exception {
        student(academy, "KimAlpha", "초등 3", "오후반");
        student(academy, "KimBeta", "초등 4", "오후반");
        student(academy, "ParkAlpha", "초등 3", "오전반");
        student(academy, "KimGamma", "초등 3", null);
        Academy foreign = academies.saveAndFlush(Academy.builder().name("다른 기관").build());
        student(foreign, "KimAlpha", "초등 3", "오후반");
        MockHttpServletRequestBuilder request = get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, teacherToken);
        query.forEach(request::param);
        JsonNode result = result(mvc.perform(request).andExpect(status().isOk()).andReturn());
        assertThat(result.get("total").asInt()).isEqualTo(expectedNames.size());
        assertThat(result.get("items").findValuesAsText("name")).containsExactlyElementsOf(expectedNames);
    }

    static Stream<Arguments> searchCases() {
        return Stream.of(
                Arguments.of(Map.of("keyword", "Kim"), List.of("KimAlpha", "KimBeta", "KimGamma")),
                Arguments.of(
                        Map.of("keyword", "  Alpha  ", "grade", " 초등 3 ", "className", " 오후반 "), List.of("KimAlpha")),
                Arguments.of(Map.of("keyword", "Kim", "grade", "초등 3"), List.of("KimAlpha", "KimGamma")),
                Arguments.of(Map.of("grade", "초등 3", "className", "오후반"), List.of("KimAlpha")),
                Arguments.of(Map.of("className", "오전반"), List.of("ParkAlpha")),
                Arguments.of(Map.of("keyword", "KimAlpha"), List.of("KimAlpha")),
                Arguments.of(Map.of("grade", "초등"), List.of()),
                Arguments.of(Map.of("className", "오후"), List.of()),
                Arguments.of(
                        Map.of("keyword", " ", "grade", " ", "className", " "),
                        List.of("KimAlpha", "KimBeta", "KimGamma", "ParkAlpha")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    @DisplayName("검색어의 SQL LIKE 특수문자는 일반 문자로 검색한다")
    void searchTreatsWildcardsAsLiteralCharacters(String character) throws Exception {
        Student exact = student(academy, "Target" + character + "Name", "초등 3", null);
        student(academy, "TargetOtherName", "초등 3", null);
        mvc.perform(get("/api/v1/students").param("keyword", character).header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(1))
                .andExpect(jsonPath("$.result.items[0].studentId").value(exact.getId()));
    }

    @Test
    @DisplayName("미배정 필터는 null·빈 반을 포함하고 다른 학년·기관은 제외한다")
    void unassignedFilter() throws Exception {
        student(academy, "A", "초등 3", null);
        student(academy, "B", "초등 3", "");
        student(academy, "C", "초등 3", "  ");
        student(academy, "D", "초등 3", "오후반");
        student(academy, "E", "초등 4", null);
        mvc.perform(get("/api/v1/students")
                        .param("unassigned", "true")
                        .param("grade", "초등 3")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(3))
                .andExpect(jsonPath("$.result.items[0].name").value("A"))
                .andExpect(jsonPath("$.result.items[0].className").isEmpty())
                .andExpect(jsonPath("$.result.items[1].className").isEmpty())
                .andExpect(jsonPath("$.result.items[2].className").isEmpty())
                .andExpect(jsonPath("$.result.items[2].name").value("C"));
    }

    @Test
    @DisplayName("페이지 간 중복 없이 조회하며 마지막·범위 밖 페이지에도 전체 개수를 유지한다")
    void pagination() throws Exception {
        for (String name : List.of("A", "B", "C", "D", "E")) {
            student(academy, name, "초등 3", null);
        }
        for (int page = 0; page < 4; page++) {
            JsonNode body = result(mvc.perform(get("/api/v1/students")
                            .param("page", String.valueOf(page))
                            .param("size", "2")
                            .header(HttpHeaders.AUTHORIZATION, teacherToken))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(body.get("total").asInt()).isEqualTo(5);
            assertThat(body.get("totalPages").asInt()).isEqualTo(3);
            assertThat(body.get("page").asInt()).isEqualTo(page);
            assertThat(body.get("hasNext").asBoolean()).isEqualTo(page < 2);
            List<String> expected =
                    switch (page) {
                        case 0 -> List.of("A", "B");
                        case 1 -> List.of("C", "D");
                        case 2 -> List.of("E");
                        default -> List.of();
                    };
            assertThat(body.get("items").findValuesAsText("name")).containsExactlyElementsOf(expected);
        }
    }

    @Test
    @DisplayName("학생이 없으면 200과 빈 목록·0건을 반환한다")
    void emptyList() throws Exception {
        mvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items").isEmpty())
                .andExpect(jsonPath("$.result.total").value(0))
                .andExpect(jsonPath("$.result.totalPages").value(0))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @ParameterizedTest
    @MethodSource("invalidSearchCases")
    @DisplayName("잘못된 검색·페이지·필터 조합은 공통 400을 반환한다")
    void invalidSearchIsBadRequest(Map<String, String> query) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, teacherToken);
        query.forEach(request::param);
        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    static Stream<Arguments> invalidSearchCases() {
        return Stream.of(
                        Map.of("page", "-1"),
                        Map.of("page", "abc"),
                        Map.of("page", "999999999999"),
                        Map.of("size", "0"),
                        Map.of("size", "101"),
                        Map.of("size", "abc"),
                        Map.of("keyword", "가".repeat(51)),
                        Map.of("grade", "가".repeat(51)),
                        Map.of("className", "가".repeat(101)),
                        Map.of("unassigned", "invalid"),
                        Map.of("unassigned", "true", "className", "오후반"),
                        Map.of("page", String.valueOf(Integer.MAX_VALUE), "size", "100"))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("원장·선생님은 같은 기관 학생의 기본 정보를 수정하고 DB에 반영한다")
    void updateAllBasicInformation(Role role) throws Exception {
        Student student = profile();
        long id = student.getId();
        JsonNode response = result(update(
                id,
                Map.of(
                        "grade",
                        "  초등 4  ",
                        "className",
                        "  오전반  ",
                        "studentPhone",
                        " 021234567 ",
                        "guardianPhone",
                        " 010-1111-2222 "),
                tokenOf(role)));
        assertThat(response.get("studentId").asLong()).isEqualTo(id);
        assertThat(response.get("name").asText()).isEqualTo("김학생");
        assertThat(response.get("grade").asText()).isEqualTo("초등 4");
        assertThat(response.get("className").asText()).isEqualTo("오전반");
        assertThat(response.get("studentPhone").asText()).isEqualTo("021234567");
        assertThat(response.get("guardianPhone").asText()).isEqualTo("010-1111-2222");
        entityManager.flush();
        entityManager.clear();
        Student saved = students.findById(id).orElseThrow();
        assertThat(saved.getGrade()).isEqualTo("초등 4");
        assertThat(saved.getClassName()).isEqualTo("오전반");
        assertThat(saved.getStudentPhone()).isEqualTo("021234567");
        assertThat(saved.getGuardianPhone()).isEqualTo("010-1111-2222");
        assertThat(saved.getAcademy().getId()).isEqualTo(academy.getId());
        assertThat(saved.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("부분 수정에서 생략한 반·연락처는 유지한다")
    void omittedFieldsAreRetained() throws Exception {
        Student student = profile();
        update(student.getId(), Map.of("grade", "초등 4"), teacherToken);
        entityManager.flush();
        entityManager.clear();
        Student saved = students.findById(student.getId()).orElseThrow();
        assertThat(saved.getGrade()).isEqualTo("초등 4");
        assertThat(saved.getClassName()).isEqualTo("오후반");
        assertThat(saved.getStudentPhone()).isEqualTo("010-1234-5678");
        assertThat(saved.getGuardianPhone()).isEqualTo("01087654321");
    }

    @ParameterizedTest
    @MethodSource("clearCases")
    @DisplayName("선택 필드의 null·빈 문자열·공백은 해당 필드만 지운다")
    void explicitEmptyClearsOnlyProvidedField(String field, String value) throws Exception {
        Student student = profile();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(field, value);
        JsonNode response = result(update(student.getId(), body, teacherToken));
        assertThat(response.get(field).isNull()).isTrue();
        entityManager.flush();
        entityManager.clear();
        Student saved = students.findById(student.getId()).orElseThrow();
        assertThat(saved.getGrade()).isEqualTo("초등 3");
        assertThat(saved.getClassName()).isEqualTo(field.equals("className") ? null : "오후반");
        assertThat(saved.getStudentPhone()).isEqualTo(field.equals("studentPhone") ? null : "010-1234-5678");
        assertThat(saved.getGuardianPhone()).isEqualTo(field.equals("guardianPhone") ? null : "01087654321");
    }

    static Stream<Arguments> clearCases() {
        return Stream.of("className", "studentPhone", "guardianPhone")
                .flatMap(field ->
                        Stream.of(Arguments.of(field, null), Arguments.of(field, ""), Arguments.of(field, " \t ")));
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateCases")
    @DisplayName("잘못된 수정 값은 400이고 다른 필드도 부분 반영하지 않는다")
    void invalidUpdateDoesNotMutateStudent(String field, String value) throws Exception {
        Student student = profile();
        Map<String, Object> body = new LinkedHashMap<>(Map.of("className", "새 반"));
        body.put(field, value);
        mvc.perform(patch("/api/v1/students/{id}", student.getId())
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        entityManager.flush();
        entityManager.clear();
        Student saved = students.findById(student.getId()).orElseThrow();
        assertThat(saved.getGrade()).isEqualTo("초등 3");
        assertThat(saved.getClassName()).isEqualTo("오후반");
        assertThat(saved.getStudentPhone()).isEqualTo("010-1234-5678");
        assertThat(saved.getGuardianPhone()).isEqualTo("01087654321");
        assertThat(saved.getVersion()).isZero();
    }

    static Stream<Arguments> invalidUpdateCases() {
        return Stream.concat(
                Stream.of(
                        Arguments.of("grade", null),
                        Arguments.of("grade", ""),
                        Arguments.of("grade", "  "),
                        Arguments.of("grade", "가".repeat(51)),
                        Arguments.of("className", "가".repeat(101))),
                Stream.of("studentPhone", "guardianPhone").flatMap(field -> Stream.of(
                                "wrong", "010-12345678", "+82-10-1234-5678", "010 1234 5678", "1".repeat(21))
                        .map(value -> Arguments.of(field, value))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "academyId", "studentId", "version", "confirmDuplicate", "unknown"})
    @DisplayName("이름·기관 등 수정 대상이 아닌 필드가 있으면 허용 필드와 함께 보내도 거부한다")
    void unsupportedFieldsAreRejected(String field) throws Exception {
        Student student = profile();
        mvc.perform(patch("/api/v1/students/{id}", student.getId())
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("grade", "초등 4", field, "changed"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        assertThat(student.getGrade()).isEqualTo("초등 3");
        assertThat(student.getName()).isEqualTo("김학생");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "", "null", "{", "[]"})
    @DisplayName("빈 수정 요청이나 잘못된 JSON은 400이다")
    void emptyOrUnreadableUpdateIsRejected(String body) throws Exception {
        Student student = profile();
        mvc.perform(patch("/api/v1/students/{id}", student.getId())
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("다른 기관 학생과 없는 학생 수정은 동일한 404이며 기존 정보가 유지된다")
    void foreignAndMissingUpdatesHaveSameNotFoundResponse(Role role) throws Exception {
        Academy foreign = academies.saveAndFlush(Academy.builder().name("다른 기관").build());
        Student target = student(foreign, "김학생", "초등 3", "오후반");
        MvcResult existing = mvc.perform(patch("/api/v1/students/{id}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenOf(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grade\":\"초등 4\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"))
                .andReturn();
        MvcResult missing = mvc.perform(patch("/api/v1/students/{id}", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, tokenOf(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grade\":\"초등 4\"}"))
                .andExpect(status().isNotFound())
                .andReturn();
        assertThat(json.readTree(existing.getResponse().getContentAsByteArray()))
                .isEqualTo(json.readTree(missing.getResponse().getContentAsByteArray()));
        assertThat(target.getGrade()).isEqualTo("초등 3");
    }

    @Test
    @DisplayName("기존 학생 정보 수정 후 상세·목록·중복 후보에 수정한 정보가 반영된다")
    void updateConnectsDetailListAndDuplicateConfirmation() throws Exception {
        Student student = profile();
        long before = students.count();
        update(student.getId(), Map.of("grade", "초등 4", "className", "오전반"), teacherToken);
        mvc.perform(get("/api/v1/students/{id}", student.getId()).header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.grade").value("초등 4"));
        mvc.perform(get("/api/v1/students")
                        .param("grade", "초등 4")
                        .param("className", "오전반")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(1));
        mvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김학생\",\"grade\":\"초등 3\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.candidates[0].studentId").value(student.getId()))
                .andExpect(jsonPath("$.error.candidates[0].grade").value("초등 4"))
                .andExpect(jsonPath("$.error.candidates[0].className").value("오전반"));
        assertThat(students.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("목록·수정은 인증이 필요하고 원장·선생님 외 역할은 거부한다")
    void newEndpointsRequireAuthenticationAndAllowedRole() throws Exception {
        Student target = profile();
        mvc.perform(get("/api/v1/students")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/students/{id}", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grade\":\"초등 4\"}"))
                .andExpect(status().isUnauthorized());
        String unsupported =
                "Bearer " + tokens.createAccessToken(Long.MAX_VALUE, academy.getId(), "other@test.com", "OTHER");
        mvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, unsupported))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        mvc.perform(patch("/api/v1/students/{id}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, unsupported)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grade\":\"초등 4\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    private Student student(Academy owner, String name, String grade, String className) {
        return students.saveAndFlush(Student.builder()
                .academy(owner)
                .name(name)
                .grade(grade)
                .className(className)
                .studentPhone("010-1234-5678")
                .guardianPhone("01087654321")
                .build());
    }

    private Student profile() {
        return student(academy, "김학생", "초등 3", "오후반");
    }

    private MvcResult update(Long id, Map<String, Object> body, String token) throws Exception {
        return mvc.perform(patch("/api/v1/students/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andReturn();
    }

    private JsonNode result(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray()).get("result");
    }

    private String tokenOf(Role role) {
        return role == Role.DIRECTOR ? directorToken : teacherToken;
    }

    private String token(Role role) {
        User user = users.saveAndFlush(User.builder()
                .academy(academy)
                .email("management_" + role + "@test.com")
                .passwordHash("hashed")
                .name("사용자")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
        return "Bearer " + tokens.createAccessToken(user.getId(), academy.getId(), user.getEmail(), role.name());
    }
}
