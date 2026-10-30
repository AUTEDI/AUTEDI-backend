package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.invitation.dto.request.InvitationCreateRequest;
import com.ceos24.autedi.domain.invitation.entity.InvitationStatus;
import com.ceos24.autedi.domain.invitation.entity.UserInvitation;
import com.ceos24.autedi.domain.invitation.repository.UserInvitationRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.ceos24.autedi.global.util.TokenHashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
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
class InvitationControllerTest {

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
    private UserInvitationRepository userInvitationRepository;

    private Academy academy;
    private User director;
    private User teacher;
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

        director = userRepository.save(User.builder()
                .academy(academy)
                .email("invitation_director@autedi.com")
                .passwordHash("hashed")
                .name("대표원장")
                .role(Role.DIRECTOR)
                .status(UserStatus.ACTIVE)
                .build());

        teacher = userRepository.save(User.builder()
                .academy(academy)
                .email("invitation_teacher@autedi.com")
                .passwordHash("hashed")
                .name("담당선생님")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());

        directorToken = "Bearer "
                + jwtTokenProvider.createAccessToken(
                        director.getId(),
                        academy.getId(),
                        director.getEmail(),
                        director.getRole().name());

        teacherToken = "Bearer "
                + jwtTokenProvider.createAccessToken(
                        teacher.getId(),
                        academy.getId(),
                        teacher.getEmail(),
                        teacher.getRole().name());
    }

    @Test
    @DisplayName("POST /api/v1/invitations - 원장(DIRECTOR) 권한으로 초대장 생성 성공 (201 Created)")
    void createInvitation_directorRole_success() throws Exception {
        InvitationCreateRequest request = new InvitationCreateRequest("new_teacher@autedi.com");

        mockMvc.perform(post("/api/v1/invitations")
                        .header(HttpHeaders.AUTHORIZATION, directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.email").value("new_teacher@autedi.com"))
                .andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.expiresAt").isNotEmpty());

        List<UserInvitation> invitations =
                userInvitationRepository.findAllByEmailAndStatus("new_teacher@autedi.com", InvitationStatus.PENDING);
        assertThat(invitations).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/v1/invitations - 일반 교사(TEACHER) 권한으로 초대장 생성 시도 시 403 Forbidden")
    void createInvitation_teacherRole_forbidden() throws Exception {
        InvitationCreateRequest request = new InvitationCreateRequest("another_teacher@autedi.com");

        mockMvc.perform(post("/api/v1/invitations")
                        .header(HttpHeaders.AUTHORIZATION, teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("POST /api/v1/invitations - 동일 이메일 재초대 시 이전 대기 토큰 REVOKED 처리 검증")
    void createInvitation_reinviteSameEmail_revokesPreviousPendingToken() throws Exception {
        String targetEmail = "reinvite_target@autedi.com";
        String oldRawToken = TokenHashUtil.generateRawToken();
        String oldTokenHash = TokenHashUtil.hashToken(oldRawToken);

        UserInvitation oldInvitation = userInvitationRepository.save(UserInvitation.builder()
                .academy(academy)
                .invitedBy(director)
                .email(targetEmail)
                .tokenHash(oldTokenHash)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build());

        assertThat(oldInvitation.getStatus()).isEqualTo(InvitationStatus.PENDING);

        InvitationCreateRequest request = new InvitationCreateRequest(targetEmail);

        mockMvc.perform(post("/api/v1/invitations")
                        .header(HttpHeaders.AUTHORIZATION, directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.email").value(targetEmail))
                .andExpect(jsonPath("$.result.status").value("PENDING"));

        // 이전 초대가 REVOKED 되었는지 검증
        UserInvitation updatedOldInvitation =
                userInvitationRepository.findById(oldInvitation.getId()).orElseThrow();
        assertThat(updatedOldInvitation.getStatus()).isEqualTo(InvitationStatus.REVOKED);

        // 이전 토큰으로 검증 시 400 Bad Request (INVITATION_003) 확인
        mockMvc.perform(get("/api/v1/invitations/validate").param("token", oldRawToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INVITATION_003"));
    }

    @Test
    @DisplayName("POST /api/v1/invitations - 이미 가입된 유저 이메일 초대 시 409 Conflict (AUTH_009)")
    void createInvitation_alreadyRegisteredEmail_conflict() throws Exception {
        InvitationCreateRequest request = new InvitationCreateRequest(teacher.getEmail());

        mockMvc.perform(post("/api/v1/invitations")
                        .header(HttpHeaders.AUTHORIZATION, directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_009"));
    }

    @Test
    @DisplayName("GET /api/v1/invitations/validate - 유효한 토큰 검증 성공 (200 OK)")
    void validateInvitation_validToken_success() throws Exception {
        String rawToken = TokenHashUtil.generateRawToken();
        String tokenHash = TokenHashUtil.hashToken(rawToken);

        userInvitationRepository.save(UserInvitation.builder()
                .academy(academy)
                .invitedBy(director)
                .email("valid_teacher@autedi.com")
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build());

        mockMvc.perform(get("/api/v1/invitations/validate").param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.valid").value(true))
                .andExpect(jsonPath("$.result.email").value("valid_teacher@autedi.com"))
                .andExpect(jsonPath("$.result.academyName").value(academy.getName()))
                .andExpect(jsonPath("$.result.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/invitations/validate - 존재하지 않는 토큰 검증 시 404 Not Found (INVITATION_001)")
    void validateInvitation_nonExistentToken_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/invitations/validate").param("token", "non-existent-token-value"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INVITATION_001"));
    }

    @Test
    @DisplayName("GET /api/v1/invitations/validate - 만료된 토큰 검증 시 410 Gone (INVITATION_002)")
    void validateInvitation_expiredToken_gone() throws Exception {
        String rawToken = TokenHashUtil.generateRawToken();
        String tokenHash = TokenHashUtil.hashToken(rawToken);

        userInvitationRepository.save(UserInvitation.builder()
                .academy(academy)
                .invitedBy(director)
                .email("expired_teacher@autedi.com")
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build());

        mockMvc.perform(get("/api/v1/invitations/validate").param("token", rawToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INVITATION_002"));
    }

    @Test
    @DisplayName("GET /api/v1/invitations/validate - 이미 취소(REVOKED)된 토큰 검증 시 400 Bad Request (INVITATION_003)")
    void validateInvitation_revokedToken_badRequest() throws Exception {
        String rawToken = TokenHashUtil.generateRawToken();
        String tokenHash = TokenHashUtil.hashToken(rawToken);

        UserInvitation invitation = UserInvitation.builder()
                .academy(academy)
                .invitedBy(director)
                .email("revoked_teacher@autedi.com")
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        invitation.revoke();
        userInvitationRepository.save(invitation);

        mockMvc.perform(get("/api/v1/invitations/validate").param("token", rawToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INVITATION_003"));
    }
}
