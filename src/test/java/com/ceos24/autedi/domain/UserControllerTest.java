package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.auth.entity.RefreshToken;
import com.ceos24.autedi.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.autedi.domain.user.dto.request.PasswordChangeRequest;
import com.ceos24.autedi.domain.user.dto.request.UserUpdateRequest;
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
class UserControllerTest {

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

    private User teacher;
    private String teacherToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Academy academy = academyRepository.save(Academy.builder()
                .name("테스트 어학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(3))
                .build());

        teacher = userRepository.save(User.builder()
                .academy(academy)
                .email("user_teacher@autedi.com")
                .passwordHash("hashed")
                .name("김선생")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());

        teacherToken = "Bearer "
                + jwtTokenProvider.createAccessToken(
                        teacher.getId(),
                        academy.getId(),
                        teacher.getEmail(),
                        teacher.getRole().name());
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me - 이름 수정 성공, 앞뒤 공백은 제거되어 저장")
    void updateMyName_success_trimmed() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserUpdateRequest("  김민수  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.userId").value(teacher.getId()))
                .andExpect(jsonPath("$.result.name").value("김민수"));

        assertThat(userRepository.findById(teacher.getId()).orElseThrow().getName())
                .isEqualTo("김민수");
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me - 공백뿐인 이름은 400")
    void updateMyName_blank_badRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserUpdateRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me - 50자를 넘는 이름은 400")
    void updateMyName_tooLong_badRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserUpdateRequest("가".repeat(51)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me - 토큰이 없으면 401")
    void updateMyName_noToken_unauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserUpdateRequest("김민수"))))
                .andExpect(status().isUnauthorized());
    }

    private void useRealPassword(String rawPassword) {
        teacher.changePassword(passwordEncoder.encode(rawPassword));
        userRepository.saveAndFlush(teacher);
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password - 변경 성공, 새 비밀번호로 저장되고 모든 Refresh Token 폐기")
    void changeMyPassword_success_revokesRefreshTokens() throws Exception {
        useRealPassword("OldPassword1!");
        refreshTokenRepository.save(RefreshToken.builder()
                .user(teacher)
                .tokenHash("a".repeat(64))
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build());

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("OldPassword1!", "NewPassword2@"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result").isEmpty());

        entityManager.flush();
        entityManager.clear();

        User saved = userRepository.findById(teacher.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword2@", saved.getPasswordHash()))
                .isTrue();
        assertThat(refreshTokenRepository
                        .findByTokenHash("a".repeat(64))
                        .orElseThrow()
                        .getRevokedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password - 현재 비밀번호가 틀리면 400 AUTH_010")
    void changeMyPassword_wrongCurrent_badRequest() throws Exception {
        useRealPassword("OldPassword1!");

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("WrongPassword1!", "NewPassword2@"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_010"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password - 새 비밀번호가 현재와 같으면 400 AUTH_011")
    void changeMyPassword_sameAsCurrent_badRequest() throws Exception {
        useRealPassword("OldPassword1!");

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("OldPassword1!", "OldPassword1!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_011"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password - 새 비밀번호가 정책을 어기면 400 COMMON_001")
    void changeMyPassword_weakNewPassword_badRequest() throws Exception {
        useRealPassword("OldPassword1!");

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(new PasswordChangeRequest("OldPassword1!", "short1!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password - 토큰이 없으면 401")
    void changeMyPassword_noToken_unauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeRequest("OldPassword1!", "NewPassword2@"))))
                .andExpect(status().isUnauthorized());
    }
}
