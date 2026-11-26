package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class StudentControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AcademyRepository academyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;
    private Academy academy;
    private String teacherToken;
    private String directorToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academyRepository.saveAndFlush(
                Academy.builder().name("학생 API 테스트 기관").build());
        teacherToken = createToken(Role.TEACHER);
        directorToken = createToken(Role.DIRECTOR);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("원장과 선생님은 자기 기관에 학생을 등록하고 Location으로 상세를 조회한다")
    void createStudentAndFollowLocation(Role role) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "  김학생  ");
        request.put("grade", "  초등 3  ");
        request.put("className", "  오후반  ");
        request.put("studentPhone", " 010-1234-5678 ");
        request.put("guardianPhone", "01087654321");

        MvcResult result = create(request, tokenOf(role));
        JsonNode created = resultBody(result);
        long studentId = created.get("studentId").asLong();
        String location = "/api/v1/students/" + studentId;

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(location);
        assertThat(created.get("name").asText()).isEqualTo("김학생");
        assertThat(created.get("grade").asText()).isEqualTo("초등 3");
        assertThat(created.get("className").asText()).isEqualTo("오후반");
        assertThat(created.get("studentPhone").asText()).isEqualTo("010-1234-5678");
        assertThat(created.get("guardianPhone").asText()).isEqualTo("01087654321");

        entityManager.flush();
        entityManager.clear();
        Student saved = studentRepository.findById(studentId).orElseThrow();
        assertThat(saved.getAcademy().getId()).isEqualTo(academy.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();

        MvcResult detail = mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, tokenOf(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andReturn();
        assertThat(resultBody(detail)).isEqualTo(created);
    }

    @Test
    @DisplayName("이름·학년만 입력하면 반·연락처 없이 등록하고 상세 조회한다")
    void createStudentWithRequiredFieldsOnly() throws Exception {
        MvcResult created = create(Map.of("name", "김학생", "grade", "미정 학년"), teacherToken);
        JsonNode body = resultBody(created);
        assertThat(body.get("className").isNull()).isTrue();
        assertThat(body.get("studentPhone").isNull()).isTrue();
        assertThat(body.get("guardianPhone").isNull()).isTrue();

        mockMvc.perform(get(created.getResponse().getHeader(HttpHeaders.LOCATION))
                        .header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.grade").value("미정 학년"))
                .andExpect(jsonPath("$.result.className").isEmpty())
                .andExpect(jsonPath("$.result.studentPhone").isEmpty())
                .andExpect(jsonPath("$.result.guardianPhone").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t "})
    @DisplayName("선택 필드의 빈 문자열과 공백은 null로 저장한다")
    void emptyOptionalFieldsAreStoredAsNull(String empty) throws Exception {
        MvcResult created = create(
                Map.of(
                        "name", "김학생",
                        "grade", "초등 3",
                        "className", empty,
                        "studentPhone", empty,
                        "guardianPhone", empty),
                teacherToken);

        entityManager.flush();
        entityManager.clear();
        Student saved = studentRepository
                .findById(resultBody(created).get("studentId").asLong())
                .orElseThrow();
        assertThat(saved.getClassName()).isNull();
        assertThat(saved.getStudentPhone()).isNull();
        assertThat(saved.getGuardianPhone()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "01012345678",
                "010-1234-5678",
                "021234567",
                "02-123-4567",
                "02-1234-5678",
                "031-123-4567",
                "070-1234-5678"
            })
    @DisplayName("학생·보호자 연락처는 국내 휴대전화와 지역번호 형식을 허용한다")
    void acceptsDomesticPhoneFormats(String phone) throws Exception {
        MvcResult result = create(
                Map.of("name", "김학생", "grade", "초등 3", "studentPhone", phone, "guardianPhone", phone), teacherToken);
        JsonNode body = resultBody(result);
        assertThat(body.get("studentPhone").asText()).isEqualTo(phone);
        assertThat(body.get("guardianPhone").asText()).isEqualTo(phone);
    }

    @ParameterizedTest(name = "{0} 검증 실패: {1}")
    @MethodSource("invalidFields")
    @DisplayName("필수값·저장 길이·연락처 형식 오류는 400이며 학생을 저장하지 않는다")
    void invalidInputDoesNotCreateStudent(String field, String value) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>(Map.of("name", "김학생", "grade", "초등 3"));
        request.put(field, value);
        long previousCount = studentRepository.count();

        mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        assertThat(studentRepository.count()).isEqualTo(previousCount);
    }

    static Stream<Arguments> invalidFields() {
        Stream<Arguments> required = Stream.of("name", "grade")
                .flatMap(field -> Stream.of(
                        Arguments.of(field, null),
                        Arguments.of(field, ""),
                        Arguments.of(field, " \t "),
                        Arguments.of(field, "가".repeat(51))));
        Stream<Arguments> phones = Stream.of("studentPhone", "guardianPhone").flatMap(field -> Stream.of(
                        "abc",
                        "010-1234-abcd",
                        "1234567890",
                        "0101234",
                        "010123456789",
                        "+82-10-1234-5678",
                        "010 1234 5678",
                        "010-12345678",
                        "0101234-5678",
                        "010--1234-5678")
                .map(phone -> Arguments.of(field, phone)));
        return Stream.concat(Stream.concat(required, phones), Stream.of(Arguments.of("className", "가".repeat(101))));
    }

    @Test
    @DisplayName("필수 필드를 생략하면 400이며 학생을 저장하지 않는다")
    void missingRequiredFieldsAreRejected() throws Exception {
        long previousCount = studentRepository.count();
        mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        assertThat(studentRepository.count()).isEqualTo(previousCount);
    }

    @Test
    @DisplayName("입력값은 기존 학생 모델의 최대 저장 길이까지 등록할 수 있다")
    void acceptsMaximumTextLengths() throws Exception {
        MvcResult result = create(
                Map.of("name", "가".repeat(50), "grade", "나".repeat(50), "className", "다".repeat(100)), teacherToken);
        assertThat(resultBody(result).get("name").asText()).hasSize(50);
        assertThat(resultBody(result).get("grade").asText()).hasSize(50);
        assertThat(resultBody(result).get("className").asText()).hasSize(100);
    }

    @Test
    @DisplayName("Body에 다른 기관 ID를 넣어도 등록 기관은 로그인 정보로 결정한다")
    void cannotChooseAnotherAcademyInRequestBody() throws Exception {
        Academy other =
                academyRepository.saveAndFlush(Academy.builder().name("다른 기관").build());
        MvcResult result = create(Map.of("name", "김학생", "grade", "초등 3", "academyId", other.getId()), teacherToken);
        long studentId = resultBody(result).get("studentId").asLong();

        entityManager.flush();
        entityManager.clear();
        Student saved = studentRepository.findById(studentId).orElseThrow();
        assertThat(saved.getAcademy().getId()).isEqualTo(academy.getId());
        assertThat(studentRepository.findByIdAndAcademyId(studentId, other.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("동명이인은 별도의 학생 ID로 등록된다")
    void sameNamesHaveDifferentStudentIds() throws Exception {
        long firstId = resultBody(create(Map.of("name", "김학생", "grade", "초등 3"), teacherToken))
                .get("studentId")
                .asLong();
        long secondId = resultBody(create(Map.of("name", "김학생", "grade", "초등 4"), teacherToken))
                .get("studentId")
                .asLong();
        assertThat(secondId).isNotEqualTo(firstId);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("같은 기관이면 다른 교사가 등록한 학생도 상세 조회할 수 있다")
    void getStudentWithinSameAcademy(Role role) throws Exception {
        Student student = saveStudent(academy);
        mockMvc.perform(get("/api/v1/students/{studentId}", student.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenOf(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.studentId").value(student.getId()))
                .andExpect(jsonPath("$.result.name").value("김학생"))
                .andExpect(jsonPath("$.result.grade").value("초등 3"))
                .andExpect(jsonPath("$.result.className").value("오후반"))
                .andExpect(jsonPath("$.result.studentPhone").value("010-1234-5678"))
                .andExpect(jsonPath("$.result.guardianPhone").value("02-123-4567"));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("다른 기관 학생과 존재하지 않는 학생은 동일한 404 응답으로 존재 여부를 숨긴다")
    void foreignAndMissingStudentsHaveSameResponse(Role role) throws Exception {
        Academy other =
                academyRepository.saveAndFlush(Academy.builder().name("다른 기관").build());
        Student foreignStudent = saveStudent(other);
        entityManager.clear();

        MvcResult foreign = mockMvc.perform(get("/api/v1/students/{studentId}", foreignStudent.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenOf(role)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"))
                .andExpect(jsonPath("$.result").isEmpty())
                .andReturn();
        MvcResult missing = mockMvc.perform(get("/api/v1/students/{studentId}", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, tokenOf(role)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"))
                .andReturn();

        assertThat(objectMapper.readTree(foreign.getResponse().getContentAsByteArray()))
                .isEqualTo(objectMapper.readTree(missing.getResponse().getContentAsByteArray()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    @DisplayName("학생 ID가 Long으로 변환되지 않으면 공통 400 응답을 반환한다")
    void invalidStudentIdIsBadRequest(String studentId) throws Exception {
        mockMvc.perform(get("/api/v1/students/{studentId}", studentId).header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null"})
    @DisplayName("등록 요청 Body가 없거나 잘못된 JSON이면 400을 반환한다")
    void unreadableRequestIsBadRequest(String body) throws Exception {
        mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("인증 없이 등록·상세 조회하면 401이며 학생을 저장하지 않는다")
    void unauthenticatedRequestsAreRejected() throws Exception {
        long previousCount = studentRepository.count();
        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김학생\",\"grade\":\"초등 3\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
        mockMvc.perform(get("/api/v1/students/{studentId}", Long.MAX_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
        assertThat(studentRepository.count()).isEqualTo(previousCount);
    }

    @Test
    @DisplayName("원장·선생님 외 역할은 학생 등록·상세 조회를 할 수 없다")
    void otherRolesAreForbidden() throws Exception {
        String token = "Bearer "
                + jwtTokenProvider.createAccessToken(
                        Long.MAX_VALUE, academy.getId(), "other@autedi.com", "SWAGGER_ADMIN");
        long previousCount = studentRepository.count();
        mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김학생\",\"grade\":\"초등 3\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        mockMvc.perform(get("/api/v1/students/{studentId}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        assertThat(studentRepository.count()).isEqualTo(previousCount);
    }

    private MvcResult create(Map<String, Object> request, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/students")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andReturn();
    }

    private JsonNode resultBody(MvcResult result) throws Exception {
        return objectMapper
                .readTree(result.getResponse().getContentAsByteArray())
                .get("result");
    }

    private String tokenOf(Role role) {
        return role == Role.DIRECTOR ? directorToken : teacherToken;
    }

    private String createToken(Role role) {
        User user = userRepository.saveAndFlush(User.builder()
                .academy(academy)
                .email("student_" + role.name().toLowerCase() + "@autedi.com")
                .passwordHash("hashed")
                .name(role == Role.DIRECTOR ? "박원장" : "김선생")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
        return "Bearer "
                + jwtTokenProvider.createAccessToken(user.getId(), academy.getId(), user.getEmail(), role.name());
    }

    private Student saveStudent(Academy owner) {
        return studentRepository.saveAndFlush(Student.builder()
                .academy(owner)
                .name("김학생")
                .grade("초등 3")
                .className("오후반")
                .studentPhone("010-1234-5678")
                .guardianPhone("02-123-4567")
                .build());
    }
}
