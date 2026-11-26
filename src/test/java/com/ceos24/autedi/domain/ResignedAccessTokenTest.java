package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.user.service.RevokedUserInitializer;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.ceos24.autedi.global.security.RevokedUserRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 차단 목록 등록은 트랜잭션 커밋 뒤에 일어나므로, 테스트 트랜잭션에 묶이지 않는 통합 테스트로 확인한다. 만든 데이터는 직접 지운다.
 */
@SpringBootTest
class ResignedAccessTokenTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AcademyRepository academyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RevokedUserRegistry revokedUserRegistry;

    @Autowired
    private RevokedUserInitializer revokedUserInitializer;

    private Academy academy;
    private User director;
    private User teacher;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        academy = academyRepository.save(Academy.builder()
                .name("차단 테스트 어학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(3))
                .build());
        director = newUser("원장", Role.DIRECTOR, UserStatus.ACTIVE);
        teacher = newUser("선생", Role.TEACHER, UserStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(user -> user.getAcademy().getId().equals(academy.getId()))
                .toList());
        academyRepository.deleteById(academy.getId());
        clearRevokedRegistry();
    }

    /** 차단 목록은 컨텍스트가 살아 있는 동안 남으므로, id가 재사용되는 다른 테스트에 영향이 없도록 비운다. */
    private void clearRevokedRegistry() {
        Cache<?, ?> revoked = (Cache<?, ?>) ReflectionTestUtils.getField(revokedUserRegistry, "revoked");
        revoked.invalidateAll();
    }

    private User newUser(String name, Role role, UserStatus status) {
        return userRepository.save(User.builder()
                .academy(academy)
                .email(name + "_" + UUID.randomUUID() + "@autedi.com")
                .passwordHash("hashed")
                .name(name)
                .role(role)
                .status(status)
                .build());
    }

    private String tokenOf(User user) {
        return "Bearer "
                + jwtTokenProvider.createAccessToken(
                        user.getId(),
                        academy.getId(),
                        user.getEmail(),
                        user.getRole().name());
    }

    @Test
    @DisplayName("퇴사 처리 전에 발급된 Access Token은 퇴사 처리 직후부터 401, 다른 사용자의 토큰은 그대로 유효")
    void resignedTeacher_existingAccessToken_isRejectedImmediately() throws Exception {
        String teacherToken = tokenOf(teacher);
        String directorToken = tokenOf(director);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/teachers/{id}/resign", teacher.getId())
                        .header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isOk());

        assertThat(revokedUserRegistry.isRevoked(teacher.getId())).isTrue();
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, directorToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("서버 재시작 직후에도 최근 퇴사자는 차단 목록으로 복원된다")
    void recentlyResignedUser_isRestoredOnStartup() {
        User resigned = newUser("최근퇴사", Role.TEACHER, UserStatus.RESIGNED);
        User active = newUser("재직", Role.TEACHER, UserStatus.ACTIVE);
        assertThat(revokedUserRegistry.isRevoked(resigned.getId())).isFalse();

        revokedUserInitializer.reload();

        assertThat(revokedUserRegistry.isRevoked(resigned.getId())).isTrue();
        assertThat(revokedUserRegistry.isRevoked(active.getId())).isFalse();
    }
}
