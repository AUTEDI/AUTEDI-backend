package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
                        user.getId(), user.getAcademy().getId(), user.getEmail(), user.getRole().name());
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
        assertThat(refreshTokenRepository.findByTokenHash("b".repeat(64)).orElseThrow().getRevokedAt())
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

        assertThat(userRepository.findById(otherAcademyTeacher.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("POST /api/v1/teachers/{id}/resign - 존재하지 않는 사용자는 404 AUTH_006")
    void resign_unknownUser_notFound() throws Exception {
        mockMvc.perform(post("/api/v1/teachers/{id}/resign", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, directorToken))
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
        mockMvc.perform(post("/api/v1/teachers/{id}/resign", teacher.getId()))
                .andExpect(status().isUnauthorized());
    }
}
