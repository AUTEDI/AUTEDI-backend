package com.ceos24.autedi.domain;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.invitation.entity.UserInvitation;
import com.ceos24.autedi.domain.invitation.repository.UserInvitationRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class TeacherListControllerTest {

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
    private UserInvitationRepository userInvitationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Academy academy;
    private Academy otherAcademy;
    private User director;
    private User teacherUser;
    private String directorToken;
    private String teacherToken;
    private int invitationSeq;

    /**
     * 같은 기관의 목록에 나와야 하는 7명: 이름 없는 초대(aaa.noname), 김선생, 박선생, 윤선생(잠금), 이초대, 정초대(만료), 최선생(퇴사). 이름순으로
     * 정렬하면 이 순서다. 이름 없는 초대는 이메일(aaa...)로 정렬되어 맨 앞에 온다.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        academy = newAcademy("테스트 어학원");
        otherAcademy = newAcademy("다른 어학원");

        director = newUser(academy, "director@autedi.com", "박원장", Role.DIRECTOR, UserStatus.ACTIVE);
        newUser(academy, "kim@autedi.com", "김선생", Role.TEACHER, UserStatus.ACTIVE);
        teacherUser = newUser(academy, "park_t@autedi.com", "박선생", Role.TEACHER, UserStatus.ACTIVE);
        newUser(academy, "yoon@autedi.com", "윤선생", Role.TEACHER, UserStatus.LOCKED);
        newUser(academy, "choi@autedi.com", "최선생", Role.TEACHER, UserStatus.RESIGNED);
        newUser(otherAcademy, "other@autedi.com", "타기관선생", Role.TEACHER, UserStatus.ACTIVE);

        newInvitation(
                academy, "이초대", "lee.invite@autedi.com", LocalDateTime.now().plusHours(10));
        newInvitation(
                academy, "정초대", "jung.invite@autedi.com", LocalDateTime.now().minusHours(5));
        newInvitation(
                academy, null, "aaa.noname@autedi.com", LocalDateTime.now().plusHours(10));
        newInvitation(academy, "취소초대", "revoked@autedi.com", LocalDateTime.now().plusHours(10))
                .revoke();
        newInvitation(
                        academy,
                        "수락초대",
                        "accepted@autedi.com",
                        LocalDateTime.now().plusHours(10))
                .accept(LocalDateTime.now());
        newInvitation(
                otherAcademy,
                "타기관초대",
                "other.invite@autedi.com",
                LocalDateTime.now().plusHours(10));

        directorToken = tokenOf(director);
        teacherToken = tokenOf(teacherUser);
    }

    private Academy newAcademy(String name) {
        return academyRepository.save(Academy.builder()
                .name(name)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(3))
                .build());
    }

    private User newUser(Academy academy, String email, String name, Role role, UserStatus status) {
        return userRepository.save(User.builder()
                .academy(academy)
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .name(name)
                .role(role)
                .status(status)
                .build());
    }

    private UserInvitation newInvitation(Academy academy, String name, String email, LocalDateTime expiresAt) {
        User inviter = academy.getId().equals(this.academy.getId()) ? director : null;
        if (inviter == null) {
            inviter = newUser(
                    academy, "inviter" + (++invitationSeq) + "@autedi.com", "타기관원장", Role.DIRECTOR, UserStatus.ACTIVE);
        }
        return userInvitationRepository.save(UserInvitation.builder()
                .academy(academy)
                .invitedBy(inviter)
                .name(name)
                .email(email)
                .tokenHash(String.format("%064d", ++invitationSeq))
                .expiresAt(expiresAt)
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

    /** 쿼리 파라미터를 "이름, 값" 순서로 받아 호출한다. 값은 인코딩되지 않은 원문이다. */
    private ResultActions list(String... nameValuePairs) throws Exception {
        MockHttpServletRequestBuilder request =
                get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, directorToken);
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            request.param(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("GET /api/v1/teachers - 가입 선생님과 초대 대기를 이름순으로 합쳐 준다. 원장·다른 기관·취소·수락 초대는 빠진다")
    void list_merged_sortedByName() throws Exception {
        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(7))
                .andExpect(jsonPath("$.result.items", hasSize(7)))
                .andExpect(jsonPath("$.result.items[0].email").value("aaa.noname@autedi.com"))
                .andExpect(jsonPath("$.result.items[1].name").value("김선생"))
                .andExpect(jsonPath("$.result.items[2].name").value("박선생"))
                .andExpect(jsonPath("$.result.items[3].name").value("윤선생"))
                .andExpect(jsonPath("$.result.items[4].name").value("이초대"))
                .andExpect(jsonPath("$.result.items[5].name").value("정초대"))
                .andExpect(jsonPath("$.result.items[6].name").value("최선생"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers - 가입 선생님 행과 초대 대기 행의 필드가 구분된다")
    void list_itemShapes() throws Exception {
        list("keyword", "kim")
                .andExpect(jsonPath("$.result.items", hasSize(1)))
                .andExpect(jsonPath("$.result.items[0].teacherId").isNumber())
                .andExpect(jsonPath("$.result.items[0].invitationId").value(nullValue()))
                .andExpect(jsonPath("$.result.items[0].registered").value(true))
                .andExpect(jsonPath("$.result.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.items[0].expiresAt").value(nullValue()));

        list("keyword", "lee.invite")
                .andExpect(jsonPath("$.result.items", hasSize(1)))
                .andExpect(jsonPath("$.result.items[0].teacherId").value(nullValue()))
                .andExpect(jsonPath("$.result.items[0].invitationId").isNumber())
                .andExpect(jsonPath("$.result.items[0].registered").value(false))
                .andExpect(jsonPath("$.result.items[0].status").value("INVITED"))
                .andExpect(jsonPath("$.result.items[0].phoneNumber").value(nullValue()))
                .andExpect(jsonPath("$.result.items[0].expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/teachers - 연락처·입사일이 등록된 선생님은 그 값을 준다")
    void list_profileFields() throws Exception {
        teacherUser.updatePhoneNumber("010-1234-5678");
        teacherUser.updateHireDate(LocalDate.of(2026, 3, 2));

        list("keyword", "park_t")
                .andExpect(jsonPath("$.result.items[0].phoneNumber").value("010-1234-5678"))
                .andExpect(jsonPath("$.result.items[0].hireDate").value("2026-03-02"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers?status=ACTIVE - 퇴사가 아닌 가입 계정(잠금 포함)만")
    void list_statusActive() throws Exception {
        list("status", "ACTIVE")
                .andExpect(jsonPath("$.result.total").value(3))
                .andExpect(jsonPath("$.result.items[0].name").value("김선생"))
                .andExpect(jsonPath("$.result.items[1].name").value("박선생"))
                .andExpect(jsonPath("$.result.items[2].name").value("윤선생"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers?status=INVITED - 초대 대기만(만료 시각이 지난 초대 포함)")
    void list_statusInvited() throws Exception {
        list("status", "INVITED")
                .andExpect(jsonPath("$.result.total").value(3))
                .andExpect(jsonPath("$.result.items[0].email").value("aaa.noname@autedi.com"))
                .andExpect(jsonPath("$.result.items[1].name").value("이초대"))
                .andExpect(jsonPath("$.result.items[2].name").value("정초대"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers?status=RESIGNED - 퇴사자만")
    void list_statusResigned() throws Exception {
        list("status", "RESIGNED")
                .andExpect(jsonPath("$.result.total").value(1))
                .andExpect(jsonPath("$.result.items[0].name").value("최선생"))
                .andExpect(jsonPath("$.result.items[0].status").value("RESIGNED"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers?keyword - 이름·이메일 부분 일치, 대소문자 무시")
    void list_keyword() throws Exception {
        list("keyword", "선생").andExpect(jsonPath("$.result.total").value(4));
        list("keyword", "초대").andExpect(jsonPath("$.result.total").value(2));
        list("keyword", "KIM").andExpect(jsonPath("$.result.total").value(1));
        list("keyword", "  kim  ").andExpect(jsonPath("$.result.total").value(1));
        list("keyword", "없는사람").andExpect(jsonPath("$.result.total").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/teachers?keyword - 공백뿐이면 무시하고, %·_는 글자 그대로 찾는다")
    void list_keyword_blankAndSpecialChars() throws Exception {
        list("keyword", "  ").andExpect(jsonPath("$.result.total").value(7));
        list("keyword", "%").andExpect(jsonPath("$.result.total").value(0));
        list("keyword", "_")
                .andExpect(jsonPath("$.result.total").value(1))
                .andExpect(jsonPath("$.result.items[0].name").value("박선생"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers - 검색과 상태 필터를 함께 쓸 수 있다")
    void list_keywordAndStatus() throws Exception {
        list("keyword", "선생", "status", "RESIGNED")
                .andExpect(jsonPath("$.result.total").value(1))
                .andExpect(jsonPath("$.result.items[0].name").value("최선생"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers - 페이지로 나눈다. 마지막 페이지는 남은 만큼, 범위 밖 페이지는 빈 목록")
    void list_paging() throws Exception {
        list("page", "0", "size", "3")
                .andExpect(jsonPath("$.result.items", hasSize(3)))
                .andExpect(jsonPath("$.result.items[0].email").value("aaa.noname@autedi.com"))
                .andExpect(jsonPath("$.result.total").value(7))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(3))
                .andExpect(jsonPath("$.result.totalPages").value(3));

        list("page", "2", "size", "3")
                .andExpect(jsonPath("$.result.items", hasSize(1)))
                .andExpect(jsonPath("$.result.items[0].name").value("최선생"));

        list("page", "5", "size", "3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(0)))
                .andExpect(jsonPath("$.result.total").value(7))
                .andExpect(jsonPath("$.result.totalPages").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/teachers - 기본값은 page=0, size=20")
    void list_defaults() throws Exception {
        list().andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20));
    }

    @Test
    @DisplayName("GET /api/v1/teachers - 잘못된 page·size·status는 400 COMMON_001")
    void list_invalidParams_badRequest() throws Exception {
        String[][] invalidQueries = {
            {"size", "0"}, {"size", "101"}, {"page", "-1"}, {"status", "FOO"}, {"page", "abc"}, {"size", "abc"}
        };
        for (String[] query : invalidQueries) {
            list(query)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_001"));
        }
    }

    @Test
    @DisplayName("GET /api/v1/teachers - 선생님이 호출하면 403 AUTH_002")
    void list_teacherCaller_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("GET /api/v1/teachers - 토큰이 없으면 401")
    void list_noToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/teachers")).andExpect(status().isUnauthorized());
    }
}
