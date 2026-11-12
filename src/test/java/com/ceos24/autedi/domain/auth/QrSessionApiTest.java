package com.ceos24.autedi.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.auth.entity.QrSession;
import com.ceos24.autedi.domain.auth.entity.QrSessionStatus;
import com.ceos24.autedi.domain.auth.event.QrSessionLinked;
import com.ceos24.autedi.domain.auth.event.QrSessionScanned;
import com.ceos24.autedi.domain.auth.repository.QrSessionRepository;
import com.ceos24.autedi.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.autedi.domain.auth.service.QrSessionService;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.ceos24.autedi.global.util.TokenHashUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 동시성 검증을 위해 클래스 레벨 @Transactional을 쓰지 않고 실제 커밋 후 정리한다. */
@SpringBootTest
@RecordApplicationEvents
class QrSessionApiTest {

    private static final String BASE = "/api/v1/auth/qr-sessions";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private QrSessionRepository qrSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademyRepository academyRepository;

    @Autowired
    private WorksheetRepository worksheetRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private QrSessionService qrSessionService;

    @Autowired
    private ApplicationEvents events;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;
    private Academy academy;
    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academyRepository.save(Academy.builder()
                .name("QR 테스트 학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(1))
                .build());
        owner = newUser("owner", UserStatus.ACTIVE);
        other = newUser("other", UserStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        qrSessionRepository.deleteAll();
        worksheetRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        academyRepository.deleteAll();
    }

    // ---------- 생성 ----------

    @Test
    @DisplayName("QR 세션 생성: 인증 없이는 401")
    void create_unauthenticated_401() throws Exception {
        mockMvc.perform(post(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("QR 세션 생성: worksheetId와 함께 201, sessionId·expiresAt·qrImage 반환, qrToken은 응답에 없음")
    void create_ok() throws Exception {
        Worksheet worksheet = saveWorksheet(owner, academy);
        ResultActions result = createRequest(owner, "{\"worksheetId\":" + worksheet.getId() + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.result.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.result.qrToken").doesNotExist())
                .andExpect(jsonPath("$.result.userId").doesNotExist());

        JsonNode body = json(result);
        assertThat(body.at("/result/qrImage").asText()).startsWith("data:image/png;base64,");
        QrSession saved = qrSessionRepository
                .findBySessionId(body.at("/result/sessionId").asText())
                .orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(QrSessionStatus.PENDING);
        assertThat(saved.getUserId()).isEqualTo(owner.getId());
        assertThat(saved.getWorksheetId()).isEqualTo(worksheet.getId());
        assertThat(saved.getQrToken()).isNotEqualTo(saved.getSessionId());
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("QR 세션 생성: worksheetId가 없으면 400 (body 없음, 빈 객체, null 모두)")
    void create_withoutWorksheetId_400() throws Exception {
        mockMvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest());
        createRequest(owner, "{}").andExpect(status().isBadRequest());
        createRequest(owner, "{\"worksheetId\":null}").andExpect(status().isBadRequest());
        assertThat(qrSessionRepository.count()).isZero();
    }

    @Test
    @DisplayName("QR 세션 생성: 없는 작업이나 다른 학원 작업은 404(WORKSHEET_001)")
    void create_unknownOrOtherAcademyWorksheet_404() throws Exception {
        createRequest(owner, "{\"worksheetId\":999999}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"));

        Academy otherAcademy = academyRepository.save(Academy.builder()
                .name("다른 학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(1))
                .build());
        User foreigner = userRepository.save(User.builder()
                .academy(otherAcademy)
                .email("foreign-" + UUID.randomUUID() + "@autedi.com")
                .passwordHash(passwordEncoder.encode("pw-1234!"))
                .name("foreign")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());
        Worksheet foreign = saveWorksheet(foreigner, otherAcademy);

        createRequest(owner, "{\"worksheetId\":" + foreign.getId() + "}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"));
        assertThat(qrSessionRepository.count()).isZero();
    }

    @Test
    @DisplayName("QR 세션 생성: 같은 학원이지만 소유자가 아닌 작업은 403(AUTH_002)")
    void create_notOwnerWorksheet_403() throws Exception {
        Worksheet othersWorksheet = saveWorksheet(other, academy);

        createRequest(owner, "{\"worksheetId\":" + othersWorksheet.getId() + "}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        assertThat(qrSessionRepository.count()).isZero();
    }

    // ---------- 비인증 엔드포인트 접근 ----------

    @Test
    @DisplayName("scan/status/token은 인증 없이 호출 가능 (없는 토큰은 404, 401 아님)")
    void unauthenticatedEndpointsAccessible() throws Exception {
        for (String path : new String[] {"/scan", "/status", "/token"}) {
            postToken(path, "no-such-token").andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("approve/reject는 인증 없이 호출하면 401")
    void approveRejectRequireAuth() throws Exception {
        mockMvc.perform(post(BASE + "/some-id/approve")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/some-id/reject")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("qrToken이 비어 있으면 400")
    void blankToken_400() throws Exception {
        mockMvc.perform(post(BASE + "/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 전체 흐름 ----------

    @Test
    @DisplayName("전체 흐름: scan → approve → token 발급 → 재호출 거부, 응답은 LoginResponse 구조")
    void fullFlow() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));

        postToken("/status", s.getQrToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.sessionId").doesNotExist())
                .andExpect(jsonPath("$.result.userId").doesNotExist());

        postToken("/scan", s.getQrToken()).andExpect(status().isOk());
        postToken("/status", s.getQrToken())
                .andExpect(jsonPath("$.result.status").value("SCANNED"));

        // SCANNED에서는 토큰 발급 불가
        postToken("/token", s.getQrToken()).andExpect(status().isConflict());

        mockMvc.perform(post(BASE + "/" + s.getSessionId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        postToken("/status", s.getQrToken())
                .andExpect(jsonPath("$.result.status").value("APPROVED"));

        ResultActions token = postToken("/token", s.getQrToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.result.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.result.user.userId").value(owner.getId()))
                .andExpect(jsonPath("$.result.user.email").value(owner.getEmail()));

        // 발급된 at는 유효, rt는 refresh 가능
        JsonNode result = json(token).at("/result");
        assertThat(jwtTokenProvider.validateToken(result.get("accessToken").asText()))
                .isTrue();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\""
                                + result.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty());

        postToken("/status", s.getQrToken())
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));
        // 두 번째 발급은 실패
        postToken("/token", s.getQrToken()).andExpect(status().isConflict());
    }

    // ---------- scan ----------

    @Test
    @DisplayName("같은 QR을 두 번 scan하면 두 번째는 409, 이벤트는 한 번만 발행 (deviceInfo는 User-Agent 추정)")
    void duplicateScan() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));

        mockMvc.perform(post(BASE + "/scan")
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(s.getQrToken())))
                .andExpect(status().isOk());
        postToken("/scan", s.getQrToken()).andExpect(status().isConflict());

        var scanned = events.stream(QrSessionScanned.class).toList();
        assertThat(scanned).hasSize(1);
        assertThat(scanned.get(0).sessionId()).isEqualTo(s.getSessionId());
        assertThat(scanned.get(0).userId()).isEqualTo(owner.getId());
        assertThat(scanned.get(0).deviceInfo()).isEqualTo("iPhone");
        assertThat(scanned.get(0).toString()).doesNotContain(s.getQrToken());
    }

    @Test
    @DisplayName("동시 scan: 성공은 정확히 1건")
    void concurrentScan() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));

        int success = runConcurrently(8, () -> qrSessionService.scan(s.getQrToken(), "Unknown"));

        assertThat(success).isEqualTo(1);
        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.SCANNED);
        assertThat(events.stream(QrSessionScanned.class).count()).isEqualTo(1);
    }

    // ---------- 만료 ----------

    @Test
    @DisplayName("만료된 세션: scan은 410이고 EXPIRED로 전환된다")
    void expiredScan() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().minusSeconds(1));

        postToken("/scan", s.getQrToken()).andExpect(status().isGone());

        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.EXPIRED);
        assertThat(events.stream(QrSessionScanned.class).count()).isZero();
    }

    @Test
    @DisplayName("만료된 세션: status 조회 시 EXPIRED, approve는 410")
    void expiredStatusAndApprove() throws Exception {
        QrSession s = saveSession(QrSessionStatus.SCANNED, LocalDateTime.now().minusSeconds(1));

        mockMvc.perform(post(BASE + "/" + s.getSessionId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isGone());
        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.EXPIRED);
        postToken("/status", s.getQrToken())
                .andExpect(jsonPath("$.result.status").value("EXPIRED"));
    }

    @Test
    @DisplayName("승인됐어도 만료 시각이 지나면 토큰 발급 불가(410) 및 토큰 미발급")
    void expiredApprovedToken() throws Exception {
        QrSession s = saveSession(QrSessionStatus.APPROVED, LocalDateTime.now().minusSeconds(1));

        postToken("/token", s.getQrToken()).andExpect(status().isGone());

        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.EXPIRED);
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("status 폴링 시 만료 시각이 지난 PENDING은 EXPIRED로 응답·저장")
    void expiredStatusPolling() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().minusSeconds(1));

        postToken("/status", s.getQrToken())
                .andExpect(jsonPath("$.result.status").value("EXPIRED"));
        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.EXPIRED);
    }

    @Test
    @DisplayName("정리 쿼리는 기준 시각 이전에 만료된 세션만 삭제")
    void cleanup() {
        QrSession old = saveSession(QrSessionStatus.EXPIRED, LocalDateTime.now().minusHours(2));
        QrSession fresh =
                saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));

        int deleted = qrSessionService.deleteStaleSessions(LocalDateTime.now().minusHours(1));

        assertThat(deleted).isEqualTo(1);
        assertThat(qrSessionRepository.findById(old.getId())).isEmpty();
        assertThat(qrSessionRepository.findById(fresh.getId())).isPresent();
    }

    // ---------- approve / reject 권한 ----------

    @Test
    @DisplayName("타 사용자의 approve/reject는 403이고 상태는 변하지 않는다")
    void nonOwnerDenied() throws Exception {
        QrSession s = saveSession(QrSessionStatus.SCANNED, LocalDateTime.now().plusMinutes(3));

        mockMvc.perform(post(BASE + "/" + s.getSessionId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + s.getSessionId() + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());

        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.SCANNED);
    }

    @Test
    @DisplayName("없는 sessionId는 404")
    void unknownSession() throws Exception {
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PENDING 상태 approve는 거부(409) — SCANNED에서만 승인 가능")
    void approveFromPendingRejected() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));

        mockMvc.perform(post(BASE + "/" + s.getSessionId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict());
        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.PENDING);
    }

    @Test
    @DisplayName("reject 후에는 scan/approve/token 모두 거부되고 status는 REJECTED")
    void rejectedIsDead() throws Exception {
        QrSession s = saveSession(QrSessionStatus.SCANNED, LocalDateTime.now().plusMinutes(3));

        mockMvc.perform(post(BASE + "/" + s.getSessionId() + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());

        postToken("/scan", s.getQrToken()).andExpect(status().isConflict());
        postToken("/token", s.getQrToken()).andExpect(status().isConflict());
        mockMvc.perform(post(BASE + "/" + s.getSessionId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict());
        postToken("/status", s.getQrToken())
                .andExpect(jsonPath("$.result.status").value("REJECTED"));
    }

    // ---------- 토큰 중복 발급 ----------

    @Test
    @DisplayName("동시 token 요청: 발급은 정확히 1건, refresh token 행도 1개만 생성")
    void concurrentTokenIssue() throws Exception {
        QrSession s = saveSession(QrSessionStatus.APPROVED, LocalDateTime.now().plusMinutes(3));
        long before = refreshTokenRepository.count();

        int success = runConcurrently(10, () -> qrSessionService.issueToken(s.getQrToken()));

        assertThat(success).isEqualTo(1);
        assertThat(refreshTokenRepository.count()).isEqualTo(before + 1);
        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("비활성 사용자는 토큰을 받지 못하고 세션은 APPROVED로 남는다")
    void inactiveUserNoToken() throws Exception {
        User locked = newUser("locked", UserStatus.LOCKED);
        QrSession s = qrSessionRepository.save(approved(locked.getId()));

        postToken("/token", s.getQrToken()).andExpect(status().isForbidden());

        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.APPROVED);
        assertThat(refreshTokenRepository.count()).isZero();
    }

    // ---------- link ----------

    @Test
    @DisplayName("link: 인증 없이 호출하면 401")
    void link_unauthenticated_401() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));

        mockMvc.perform(post(BASE + "/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(s.getQrToken())))
                .andExpect(status().isUnauthorized());
        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.PENDING);
    }

    @Test
    @DisplayName("link: 같은 사용자는 승인 없이 성공, /token과 같은 응답 형식, 새 토큰에 새 qrSessionId, qr.linked 발행")
    void link_sameUser_ok() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));

        ResultActions result = linkRequest(bearer(owner), s.getQrToken(), "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.result.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.result.user.userId").value(owner.getId()))
                .andExpect(jsonPath("$.result.user.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.result.user.academyId").value(academy.getId()));

        // /token과 같은 응답 구조(result 키 집합)
        QrSession approvedSession = qrSessionRepository.save(approved(owner.getId()));
        JsonNode tokenResult = json(postToken("/token", approvedSession.getQrToken())
                        .andExpect(status().isOk()))
                .at("/result");
        JsonNode linkResult = json(result).at("/result");
        assertThat(fieldNames(linkResult)).isEqualTo(fieldNames(tokenResult));
        assertThat(fieldNames(linkResult.get("user"))).isEqualTo(fieldNames(tokenResult.get("user")));

        // 새 액세스 토큰과 리프레시 토큰에 새 qrSessionId
        String accessToken = linkResult.get("accessToken").asText();
        var principal = (com.ceos24.autedi.global.security.CustomUserPrincipal)
                jwtTokenProvider.getAuthentication(accessToken).getPrincipal();
        assertThat(principal.qrSessionId()).isEqualTo(s.getSessionId());
        String tokenHash =
                TokenHashUtil.hashToken(linkResult.get("refreshToken").asText());
        assertThat(refreshTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow()
                        .getQrSessionId())
                .isEqualTo(s.getSessionId());

        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.COMPLETED);
        postToken("/token", s.getQrToken()).andExpect(status().isConflict());

        var linked = events.stream(QrSessionLinked.class).toList();
        assertThat(linked).hasSize(1);
        assertThat(linked.get(0).userId()).isEqualTo(owner.getId());
        assertThat(linked.get(0).sessionId()).isEqualTo(s.getSessionId());
        assertThat(linked.get(0).deviceInfo()).isEqualTo("iPhone");
        assertThat(linked.get(0).toString()).doesNotContain(s.getQrToken()).doesNotContain(accessToken);
    }

    @Test
    @DisplayName("link: 다른 사용자는 QR_005(403)로 거절되고 세션은 PENDING 그대로여서 정상 흐름이 계속된다")
    void link_otherUser_rejected() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));
        long tokensBefore = refreshTokenRepository.count();

        linkRequest(bearer(other), s.getQrToken(), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QR_005"));

        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.PENDING);
        assertThat(refreshTokenRepository.count()).isEqualTo(tokensBefore);
        assertThat(events.stream(QrSessionLinked.class).count()).isZero();

        // 정상 흐름: scan → approve → token
        postToken("/scan", s.getQrToken()).andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/" + s.getSessionId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        postToken("/token", s.getQrToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("link: 없는 qrToken은 404, 만료는 410(EXPIRED 전환), 이미 사용/스캔된 세션은 409")
    void link_invalidSessions() throws Exception {
        linkRequest(bearer(owner), "no-such-token", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_001"));

        QrSession expired =
                saveSession(QrSessionStatus.PENDING, LocalDateTime.now().minusSeconds(1));
        linkRequest(bearer(owner), expired.getQrToken(), null)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("QR_002"));
        assertThat(reload(expired).getStatus()).isEqualTo(QrSessionStatus.EXPIRED);

        for (QrSessionStatus used : new QrSessionStatus[] {
            QrSessionStatus.SCANNED, QrSessionStatus.APPROVED, QrSessionStatus.COMPLETED, QrSessionStatus.REJECTED
        }) {
            QrSession s = saveSession(used, LocalDateTime.now().plusMinutes(3));
            linkRequest(bearer(owner), s.getQrToken(), null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("QR_003"));
            assertThat(reload(s).getStatus()).isEqualTo(used);
        }
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("link: 같은 QR을 두 번 link하면 두 번째는 409")
    void link_twice() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));

        linkRequest(bearer(owner), s.getQrToken(), null).andExpect(status().isOk());
        linkRequest(bearer(owner), s.getQrToken(), null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("동시 link: 성공은 정확히 1건, refresh token 행도 1개, 이벤트도 1건")
    void concurrentLink() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));
        long before = refreshTokenRepository.count();

        int success = runConcurrently(8, () -> qrSessionService.link(owner.getId(), s.getQrToken(), "iPhone"));

        assertThat(success).isEqualTo(1);
        assertThat(refreshTokenRepository.count()).isEqualTo(before + 1);
        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.COMPLETED);
        assertThat(events.stream(QrSessionLinked.class).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("link와 scan이 동시에 와도 PENDING 전이를 가져간 쪽 하나만 성공한다")
    void concurrentLinkAndScan() throws Exception {
        QrSession s = saveSession(QrSessionStatus.PENDING, LocalDateTime.now().plusMinutes(3));

        AtomicInteger counter = new AtomicInteger();
        int success = runConcurrently(8, () -> {
            if (counter.getAndIncrement() % 2 == 0) {
                qrSessionService.link(owner.getId(), s.getQrToken(), "iPhone");
            } else {
                qrSessionService.scan(s.getQrToken(), "iPhone");
            }
        });

        assertThat(success).isEqualTo(1);
        assertThat(reload(s).getStatus()).isIn(QrSessionStatus.COMPLETED, QrSessionStatus.SCANNED);
    }

    @Test
    @DisplayName("link: 비활성 사용자는 거절되고 세션은 PENDING으로 남는다")
    void link_inactiveUser() throws Exception {
        User locked = newUser("locked", UserStatus.LOCKED);
        QrSession s = qrSessionRepository.save(QrSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .qrToken(TokenHashUtil.generateRawToken())
                .userId(locked.getId())
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build());

        linkRequest(bearer(locked), s.getQrToken(), null).andExpect(status().isForbidden());

        assertThat(reload(s).getStatus()).isEqualTo(QrSessionStatus.PENDING);
        assertThat(refreshTokenRepository.count()).isZero();
    }

    // ---------- 기존 로그인 호환 ----------

    @Test
    @DisplayName("기존 로그인 응답 구조 유지 및 rt refresh/logout 동작")
    void existingLoginUnchanged() throws Exception {
        ResultActions login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + owner.getEmail() + "\",\"password\":\"pw-1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.result.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.result.user.userId").value(owner.getId()))
                .andExpect(jsonPath("$.result.user.academyId").value(academy.getId()))
                .andExpect(jsonPath("$.result.user.role").value("TEACHER"));
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(userRepository.findById(owner.getId()).orElseThrow().getLastLoginAt())
                .isNotNull();

        String rt = json(login).at("/result/refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rt + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.refreshToken").isNotEmpty());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rt + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + owner.getEmail() + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private User newUser(String prefix, UserStatus userStatus) {
        return userRepository.save(User.builder()
                .academy(academy)
                .email(prefix + "-" + UUID.randomUUID() + "@autedi.com")
                .passwordHash(passwordEncoder.encode("pw-1234!"))
                .name(prefix)
                .role(Role.TEACHER)
                .status(userStatus)
                .build());
    }

    private QrSession approved(Long userId) {
        QrSession s = QrSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .qrToken(TokenHashUtil.generateRawToken())
                .userId(userId)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build();
        s.scan();
        s.approve();
        return s;
    }

    private QrSession saveSession(QrSessionStatus target, LocalDateTime expiresAt) {
        QrSession s = QrSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .qrToken(TokenHashUtil.generateRawToken())
                .userId(owner.getId())
                .expiresAt(expiresAt)
                .build();
        switch (target) {
            case PENDING -> {}
            case SCANNED -> s.scan();
            case APPROVED -> {
                s.scan();
                s.approve();
            }
            case COMPLETED -> {
                s.scan();
                s.approve();
                s.complete();
            }
            case REJECTED -> s.reject();
            case EXPIRED -> s.expire();
        }
        return qrSessionRepository.save(s);
    }

    private QrSession reload(QrSession s) {
        return qrSessionRepository.findById(s.getId()).orElseThrow();
    }

    private ResultActions createRequest(User user, String body) throws Exception {
        return mockMvc.perform(post(BASE)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Worksheet saveWorksheet(User user, Academy owningAcademy) {
        return worksheetRepository.save(Worksheet.builder()
                .academy(owningAcademy)
                .user(user)
                .originalFilename("sample.png")
                .fileUrl("http://example.com/sample.png")
                .fileType(FileType.PNG)
                .fileSize(1024L)
                .fileHash("hash-" + UUID.randomUUID())
                .pageCount(1)
                .build());
    }

    private String bearer(User user) {
        return "Bearer "
                + jwtTokenProvider.createAccessToken(
                        user.getId(),
                        academy.getId(),
                        user.getEmail(),
                        user.getRole().name());
    }

    private ResultActions linkRequest(String authorization, String qrToken, String userAgent) throws Exception {
        var request = post(BASE + "/link")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tokenBody(qrToken));
        if (userAgent != null) {
            request.header(HttpHeaders.USER_AGENT, userAgent);
        }
        return mockMvc.perform(request);
    }

    private java.util.List<String> fieldNames(JsonNode node) {
        var names = new java.util.ArrayList<String>();
        node.fieldNames().forEachRemaining(names::add);
        java.util.Collections.sort(names);
        return names;
    }

    private String tokenBody(String qrToken) {
        return "{\"qrToken\":\"" + qrToken + "\"}";
    }

    private ResultActions postToken(String path, String qrToken) throws Exception {
        return mockMvc.perform(
                post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(tokenBody(qrToken)));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** 모든 스레드를 동시에 출발시켜 성공(예외 없음) 횟수를 반환한다. */
    private int runConcurrently(int threads, Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        task.run();
                        success.incrementAndGet();
                    } catch (CustomException expected) {
                        // 실패 처리된 요청
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            ready.await();
            go.countDown();
            for (var f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        return success.get();
    }
}
