package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.auth.dto.request.LoginRequest;
import com.ceos24.autedi.domain.auth.entity.RefreshToken;
import com.ceos24.autedi.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class TeacherControllerTest {

    private static final String PASSWORD = "Password123!";

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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    private Academy academy;
    private Academy otherAcademy;
    private User director;
    private User teacher;
    private User otherAcademyTeacher;
    private String directorToken;
    private String teacherToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        academy = newAcademy("테스트 어학원");
        otherAcademy = newAcademy("다른 어학원");

        director = newUser(academy, "resign_director@autedi.com", "박원장", Role.DIRECTOR);
        teacher = newUser(academy, "resign_teacher@autedi.com", "김선생", Role.TEACHER);
        otherAcademyTeacher = newUser(otherAcademy, "resign_other@autedi.com", "타기관선생", Role.TEACHER);

        directorToken = tokenOf(director);
        teacherToken = tokenOf(teacher);
    }

    private Academy newAcademy(String name) {
        return academyRepository.save(Academy.builder()
                .name(name)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(3))
                .build());
    }

    private User newUser(Academy academy, String email, String name, Role role) {
        return userRepository.save(User.builder()
                .academy(academy)
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .name(name)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private String tokenOf(User user) {
        return "Bearer "
                + jwtTokenProvider.createAccessToken(
                        user.getId(),
                        user.getAcademy().getId(),
                        user.getEmail(),
                        user.getRole().name());
    }

    @Test
    @DisplayName("POST /api/v1/teachers/{id}/resign - 원장이 퇴사 처리, 상태 변경과 Refresh Token 폐기, 이후 로그인 차단")
    void resign_director_success() throws Exception {
        refreshTokenRepository.save(RefreshToken.builder()
                .user(teacher)
                .tokenHash("b".repeat(64))
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build());

        mockMvc.perform(post("/api/v1/teachers/{id}/resign", teacher.getId())
                        .header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.teacherId").value(teacher.getId()))
                .andExpect(jsonPath("$.result.status").value("RESIGNED"));

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findById(teacher.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.RESIGNED);
        assertThat(refreshTokenRepository
                        .findByTokenHash("b".repeat(64))
                        .orElseThrow()
                        .getRevokedAt())
                .isNotNull();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(teacher.getEmail(), PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_012"));
    }

    @Test
    @DisplayName("POST /api/v1/teachers/{id}/resign - 선생님이 호출하면 403 AUTH_002, 상태는 그대로")
    void resign_teacherCaller_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/teachers/{id}/resign", teacher.getId())
                        .header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        assertThat(userRepository.findById(teacher.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("POST /api/v1/teachers/{id}/resign - 다른 기관 선생님은 404 AUTH_006, 상태는 그대로")
    void resign_otherAcademyTeacher_notFound() throws Exception {
        mockMvc.perform(post("/api/v1/teachers/{id}/resign", otherAcademyTeacher.getId())
                        .header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_006"));

        assertThat(userRepository
                        .findById(otherAcademyTeacher.getId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("POST /api/v1/teachers/{id}/resign - 존재하지 않는 사용자는 404 AUTH_006")
    void resign_unknownUser_notFound() throws Exception {
        mockMvc.perform(post("/api/v1/teachers/{id}/resign", 999_999L).header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("POST /api/v1/teachers/{id}/resign - 원장(본인 포함)은 퇴사 처리할 수 없어 403 AUTH_002")
    void resign_directorTarget_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/teachers/{id}/resign", director.getId())
                        .header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        assertThat(userRepository.findById(director.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("POST /api/v1/teachers/{id}/resign - 이미 퇴사한 선생님은 409 AUTH_013")
    void resign_alreadyResigned_conflict() throws Exception {
        mockMvc.perform(post("/api/v1/teachers/{id}/resign", teacher.getId())
                        .header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/teachers/{id}/resign", teacher.getId())
                        .header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_013"));
    }

    @Test
    @DisplayName("POST /api/v1/teachers/{id}/resign - 토큰이 없으면 401")
    void resign_noToken_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/teachers/{id}/resign", teacher.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/teachers/{id} - 원장이 같은 기관 선생님을 조회하면 이름·이메일·연락처·입사일·상태를 받는다")
    void getDetail_director_success() throws Exception {
        teacher.updatePhoneNumber("010-1234-5678");
        teacher.updateHireDate(LocalDate.of(2026, 3, 2));

        mockMvc.perform(get("/api/v1/teachers/{id}", teacher.getId()).header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.teacherId").value(teacher.getId()))
                .andExpect(jsonPath("$.result.name").value("김선생"))
                .andExpect(jsonPath("$.result.email").value("resign_teacher@autedi.com"))
                .andExpect(jsonPath("$.result.phoneNumber").value("010-1234-5678"))
                .andExpect(jsonPath("$.result.hireDate").value("2026-03-02"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers/{id} - 연락처·입사일을 등록하지 않았으면 null로 내려준다")
    void getDetail_withoutProfile_nullFields() throws Exception {
        mockMvc.perform(get("/api/v1/teachers/{id}", teacher.getId()).header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.phoneNumber").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.result.hireDate").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/teachers/{id} - 퇴사한 선생님도 조회할 수 있고 상태는 RESIGNED")
    void getDetail_resignedTeacher_success() throws Exception {
        teacher.resign();

        mockMvc.perform(get("/api/v1/teachers/{id}", teacher.getId()).header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("RESIGNED"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers/{id} - 선생님이 호출하면 403 AUTH_002")
    void getDetail_teacherCaller_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/teachers/{id}", teacher.getId()).header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers/{id} - 다른 기관 선생님은 404 AUTH_006")
    void getDetail_otherAcademyTeacher_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/teachers/{id}", otherAcademyTeacher.getId())
                        .header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers/{id} - 존재하지 않는 사용자는 404 AUTH_006")
    void getDetail_unknownUser_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/teachers/{id}", 999_999L).header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers/{id} - 원장 ID를 조회하면 403 AUTH_002")
    void getDetail_directorTarget_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/teachers/{id}", director.getId()).header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers/{id} - 토큰이 없으면 401")
    void getDetail_noToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/teachers/{id}", teacher.getId())).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions patchTeacher(Long id, String token, String body)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/teachers/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private User reload(User user) {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(user.getId()).orElseThrow();
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 이름·연락처·입사일을 모두 수정하면 응답과 DB가 바뀌고 이메일은 그대로")
    void update_all_success() throws Exception {
        patchTeacher(
                        teacher.getId(),
                        directorToken,
                        """
                        {"name":"김민수","phoneNumber":"010-1234-5678","hireDate":"2026-03-02"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("김민수"))
                .andExpect(jsonPath("$.result.phoneNumber").value("010-1234-5678"))
                .andExpect(jsonPath("$.result.hireDate").value("2026-03-02"))
                .andExpect(jsonPath("$.result.email").value("resign_teacher@autedi.com"));

        User saved = reload(teacher);
        assertThat(saved.getName()).isEqualTo("김민수");
        assertThat(saved.getPhoneNumber()).isEqualTo("010-1234-5678");
        assertThat(saved.getHireDate()).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 이름만 보내면 연락처·입사일은 그대로")
    void update_nameOnly_keepsOthers() throws Exception {
        teacher.updatePhoneNumber("010-0000-0000");
        teacher.updateHireDate(LocalDate.of(2025, 1, 1));

        patchTeacher(teacher.getId(), directorToken, "{\"name\":\"김민수\"}").andExpect(status().isOk());

        User saved = reload(teacher);
        assertThat(saved.getName()).isEqualTo("김민수");
        assertThat(saved.getPhoneNumber()).isEqualTo("010-0000-0000");
        assertThat(saved.getHireDate()).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 이름 앞뒤 공백은 제거되어 저장")
    void update_name_trimmed() throws Exception {
        patchTeacher(teacher.getId(), directorToken, "{\"name\":\"  김민수  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("김민수"));

        assertThat(reload(teacher).getName()).isEqualTo("김민수");
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 연락처를 빈 문자열로 보내면 삭제(null)")
    void update_emptyPhone_clears() throws Exception {
        teacher.updatePhoneNumber("010-0000-0000");

        patchTeacher(teacher.getId(), directorToken, "{\"phoneNumber\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.phoneNumber").value(org.hamcrest.Matchers.nullValue()));

        assertThat(reload(teacher).getPhoneNumber()).isNull();
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 퇴사한 선생님도 수정할 수 있고 상태는 그대로")
    void update_resignedTeacher_success() throws Exception {
        teacher.resign();

        patchTeacher(teacher.getId(), directorToken, "{\"name\":\"김민수\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("RESIGNED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 요청에 email을 넣어도 이메일은 바뀌지 않는다")
    void update_emailIgnored() throws Exception {
        patchTeacher(teacher.getId(), directorToken, "{\"name\":\"김민수\",\"email\":\"hacked@autedi.com\"}")
                .andExpect(status().isOk());

        assertThat(reload(teacher).getEmail()).isEqualTo("resign_teacher@autedi.com");
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 잘못된 입력은 400 COMMON_001이고 데이터는 그대로")
    void update_invalidInput_badRequest() throws Exception {
        String[] bodies = {
            "{}",
            "{\"name\":\"   \"}",
            "{\"name\":\"" + "가".repeat(51) + "\"}",
            "{\"phoneNumber\":\"abc-1234\"}",
            "{\"phoneNumber\":\"123456\"}",
            "{\"phoneNumber\":\"" + "1".repeat(21) + "\"}",
            "{\"hireDate\":\"2026-13-45\"}",
            "{\"hireDate\":\"2026/03/02\"}"
        };
        for (String body : bodies) {
            patchTeacher(teacher.getId(), directorToken, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_001"));
        }

        User saved = reload(teacher);
        assertThat(saved.getName()).isEqualTo("김선생");
        assertThat(saved.getPhoneNumber()).isNull();
        assertThat(saved.getHireDate()).isNull();
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 선생님이 호출하면 403 AUTH_002, 데이터는 그대로")
    void update_teacherCaller_forbidden() throws Exception {
        patchTeacher(teacher.getId(), teacherToken, "{\"name\":\"김민수\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        assertThat(reload(teacher).getName()).isEqualTo("김선생");
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 다른 기관 선생님은 404 AUTH_006, 데이터는 그대로")
    void update_otherAcademyTeacher_notFound() throws Exception {
        patchTeacher(otherAcademyTeacher.getId(), directorToken, "{\"name\":\"김민수\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_006"));

        assertThat(reload(otherAcademyTeacher).getName()).isEqualTo("타기관선생");
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 존재하지 않는 사용자는 404 AUTH_006")
    void update_unknownUser_notFound() throws Exception {
        patchTeacher(999_999L, directorToken, "{\"name\":\"김민수\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 원장 ID이면 403 AUTH_002, 데이터는 그대로")
    void update_directorTarget_forbidden() throws Exception {
        patchTeacher(director.getId(), directorToken, "{\"name\":\"김민수\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        assertThat(reload(director).getName()).isEqualTo("박원장");
    }

    @Test
    @DisplayName("PATCH /api/v1/teachers/{id} - 토큰이 없으면 401")
    void update_noToken_unauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/teachers/{id}", teacher.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김민수\"}"))
                .andExpect(status().isUnauthorized());
    }
}
