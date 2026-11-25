package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.dto.request.AcademyUpdateRequest;
import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class AcademyControllerTest {

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

    private Academy academy;
    private String directorToken;
    private String teacherToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        academy = academyRepository.save(Academy.builder()
                .name("테스트 어학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(3))
                .build());

        User director = createUser("academy_director@autedi.com", "박원장", Role.DIRECTOR);
        User teacher = createUser("academy_teacher@autedi.com", "김선생", Role.TEACHER);

        directorToken = tokenOf(director);
        teacherToken = tokenOf(teacher);
    }

    private User createUser(String email, String name, Role role) {
        return userRepository.save(User.builder()
                .academy(academy)
                .email(email)
                .passwordHash("hashed")
                .name(name)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private String tokenOf(User user) {
        return "Bearer "
                + jwtTokenProvider.createAccessToken(
                        user.getId(), academy.getId(), user.getEmail(), user.getRole().name());
    }

    @Test
    @DisplayName("PATCH /api/v1/academies/me - 원장은 기관명 수정 성공, 앞뒤 공백은 제거되어 저장")
    void updateAcademyName_director_success_trimmed() throws Exception {
        mockMvc.perform(patch("/api/v1/academies/me")
                        .header(HttpHeaders.AUTHORIZATION, directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcademyUpdateRequest("  에이플러스 학원  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.academyId").value(academy.getId()))
                .andExpect(jsonPath("$.result.name").value("에이플러스 학원"));

        assertThat(academyRepository.findById(academy.getId()).orElseThrow().getName())
                .isEqualTo("에이플러스 학원");
    }

    @Test
    @DisplayName("PATCH /api/v1/academies/me - 선생님은 403 AUTH_002")
    void updateAcademyName_teacher_forbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/academies/me")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcademyUpdateRequest("에이플러스 학원"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        assertThat(academyRepository.findById(academy.getId()).orElseThrow().getName())
                .isEqualTo("테스트 어학원");
    }

    @Test
    @DisplayName("PATCH /api/v1/academies/me - 공백뿐인 이름은 400")
    void updateAcademyName_blank_badRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/academies/me")
                        .header(HttpHeaders.AUTHORIZATION, directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcademyUpdateRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("PATCH /api/v1/academies/me - 100자를 넘는 이름은 400")
    void updateAcademyName_tooLong_badRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/academies/me")
                        .header(HttpHeaders.AUTHORIZATION, directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcademyUpdateRequest("가".repeat(101)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("PATCH /api/v1/academies/me - 토큰이 없으면 401")
    void updateAcademyName_noToken_unauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/academies/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcademyUpdateRequest("에이플러스 학원"))))
                .andExpect(status().isUnauthorized());
    }
}
