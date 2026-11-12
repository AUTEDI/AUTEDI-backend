package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.auth.dto.request.TokenRefreshRequest;
import com.ceos24.autedi.domain.auth.entity.QrSession;
import com.ceos24.autedi.domain.auth.entity.RefreshToken;
import com.ceos24.autedi.domain.auth.repository.QrSessionRepository;
import com.ceos24.autedi.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.autedi.domain.auth.service.AuthService;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.event.WorksheetCreated;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSettingRepository;
import com.ceos24.autedi.global.security.CustomUserPrincipal;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.ceos24.autedi.global.sse.SseEmitterRegistry;
import com.ceos24.autedi.global.sse.SseHeartbeatScheduler;
import com.ceos24.autedi.global.util.TokenHashUtil;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** 폰(QR 로그인 토큰) 업로드의 대상 검증과 worksheet.created / file_replaced SSE 전달을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorksheetQrUploadEventTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SseEmitterRegistry registry;

    @Autowired
    private SseHeartbeatScheduler heartbeat;

    @Autowired
    private QrSessionRepository qrSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademyRepository academyRepository;

    @Autowired
    private WorksheetRepository worksheetRepository;

    @Autowired
    private WorksheetPageRepository worksheetPageRepository;

    @Autowired
    private WorksheetSettingRepository worksheetSettingRepository;

    @Autowired
    private WorksheetGenerationRepository generationRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private final HttpClient http =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final List<SseClient> clients = new CopyOnWriteArrayList<>();

    private MockMvc mockMvc;
    private Academy academy;
    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        registry.snapshotAll().forEach(uc -> registry.close(uc.userId(), uc.connection()));
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academyRepository.save(Academy.builder()
                .name("QR 업로드 테스트 학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(1))
                .build());
        owner = newUser("owner");
        other = newUser("other");
    }

    @AfterEach
    void tearDown() {
        clients.forEach(SseClient::close);
        generationRepository.deleteAll();
        worksheetPageRepository.deleteAll();
        worksheetSettingRepository.deleteAll();
        worksheetRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        qrSessionRepository.deleteAll();
        userRepository.deleteAll();
        academyRepository.deleteAll();
    }

    @Test
    @DisplayName("QR 세션 사용자의 새 작업 업로드는 허용되고, 소유자 SSE로 worksheet.created가 오며 다른 사용자는 받지 않는다")
    void qrUserUploadCreatesWorksheetAndNotifiesOwner() throws Exception {
        SseClient mine = connect(owner);
        SseClient others = connect(other);
        QrSession session = saveSession(owner.getId(), null);

        String body = upload("/api/v1/worksheets", qrToken(owner, session), "files", 201);
        long worksheetId = idOf(body);

        await(() -> mine.lines.contains("event:worksheet.created"));
        String data = dataLine(mine);
        assertThat(data).contains("\"sessionId\":\"" + session.getSessionId() + "\"");
        assertThat(data).contains("\"worksheetId\":" + worksheetId);
        assertThat(data).doesNotContain(session.getQrToken());

        heartbeat.runOnce();
        await(() -> others.lines.contains(": ping"));
        assertThat(others.lines.stream().anyMatch(l -> l.startsWith("event:"))).isFalse();
    }

    @Test
    @DisplayName("업로드 후 첨삭이 자동 시작되지 않는다")
    void uploadDoesNotStartGeneration() throws Exception {
        QrSession session = saveSession(owner.getId(), null);

        long worksheetId = idOf(upload("/api/v1/worksheets", qrToken(owner, session), "files", 201));

        assertThat(generationRepository.count()).isZero();
        assertThat(worksheetRepository.findById(worksheetId).orElseThrow().getStatus())
                .isEqualTo(WorksheetStatus.DRAFT);
    }

    @Test
    @DisplayName("worksheet_id가 지정된 QR 세션으로는 새 작업을 만들 수 없다")
    void targetedSessionRejectsNewUpload() throws Exception {
        long existing = idOf(upload("/api/v1/worksheets", plainToken(owner), "files", 201));
        QrSession session = saveSession(owner.getId(), existing);

        upload("/api/v1/worksheets", qrToken(owner, session), "files", 403);

        assertThat(worksheetRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("지정된 작업의 파일 교체는 허용되고 worksheet.file_replaced가 오며, 다른 작업 교체는 거부된다")
    void targetedSessionReplacesOnlyItsWorksheet() throws Exception {
        long target = idOf(upload("/api/v1/worksheets", plainToken(owner), "files", 201));
        long another = idOf(upload("/api/v1/worksheets", plainToken(owner), "files", 201));
        QrSession session = saveSession(owner.getId(), target);
        SseClient mine = connect(owner);

        upload("/api/v1/worksheets/" + another + "/file", qrToken(owner, session), "file", 403, true);
        upload("/api/v1/worksheets/" + target + "/file", qrToken(owner, session), "file", 200, true);

        await(() -> mine.lines.contains("event:worksheet.file_replaced"));
        String data = dataLine(mine);
        assertThat(data).contains("\"sessionId\":\"" + session.getSessionId() + "\"");
        assertThat(data).contains("\"worksheetId\":" + target);
        assertThat(mine.lines.stream().filter(l -> l.startsWith("event:")).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("다른 사용자의 작업은 교체할 수 없다")
    void cannotReplaceAnotherUsersWorksheet() throws Exception {
        long ownersWorksheet = idOf(upload("/api/v1/worksheets", plainToken(owner), "files", 201));
        QrSession othersSession = saveSession(other.getId(), null);

        upload("/api/v1/worksheets/" + ownersWorksheet + "/file", qrToken(other, othersSession), "file", 403, true);
    }

    @Test
    @DisplayName("토큰 사용자와 QR 세션 소유자가 다르면 거부된다")
    void sessionOwnerMismatchRejected() throws Exception {
        QrSession ownersSession = saveSession(owner.getId(), null);

        upload("/api/v1/worksheets", qrToken(other, ownersSession), "files", 403);
    }

    @Test
    @DisplayName("일반 로그인 토큰의 업로드는 기존과 같이 동작하고 이벤트를 보내지 않는다")
    void plainUploadUnchangedAndSilent() throws Exception {
        SseClient mine = connect(owner);
        saveSession(owner.getId(), 9999L); // 같은 사용자의 대상 지정 QR 세션이 있어도 일반 토큰에는 영향이 없다.

        upload("/api/v1/worksheets", plainToken(owner), "files", 201);

        heartbeat.runOnce();
        await(() -> mine.lines.contains(": ping"));
        assertThat(mine.lines.stream().anyMatch(l -> l.startsWith("event:"))).isFalse();
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 worksheet.created는 전송되지 않는다")
    void rollbackNotDelivered() throws Exception {
        SseClient mine = connect(owner);

        tx.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new WorksheetCreated(owner.getId(), "rolled-back", 1L));
            status.setRollbackOnly();
        });
        heartbeat.runOnce();

        await(() -> mine.lines.contains(": ping"));
        assertThat(mine.lines.stream().anyMatch(l -> l.startsWith("event:"))).isFalse();
    }

    @Test
    @DisplayName("QR 로그인 refresh 토큰은 QR 세션이 만료돼도 토큰 갱신 때마다 QR 표시를 그대로 이어간다")
    void refreshKeepsQrSessionEvenAfterExpiry() {
        QrSession session = saveSession(owner.getId(), null);
        String raw = TokenHashUtil.generateRawToken();
        saveRefresh(raw, session.getSessionId());

        var first = authService.refresh(new TokenRefreshRequest(raw));
        assertThat(principalOf(first.accessToken()).qrSessionId()).isEqualTo(session.getSessionId());

        tx.executeWithoutResult(s -> qrSessionRepository
                .findBySessionId(session.getSessionId())
                .ifPresent(q -> org.springframework.test.util.ReflectionTestUtils.setField(
                        q, "expiresAt", LocalDateTime.now().minusMinutes(1))));
        qrSessionRepository.flush();

        var second = authService.refresh(new TokenRefreshRequest(first.refreshToken()));
        assertThat(principalOf(second.accessToken()).qrSessionId()).isEqualTo(session.getSessionId());
        var third = authService.refresh(new TokenRefreshRequest(second.refreshToken()));
        assertThat(principalOf(third.accessToken()).qrSessionId()).isEqualTo(session.getSessionId());
    }

    @Test
    @DisplayName("일반 로그인 refresh 토큰은 갱신 후에도 일반 토큰이다")
    void refreshKeepsPlainTokenPlain() {
        String raw = TokenHashUtil.generateRawToken();
        saveRefresh(raw, null);

        var refreshed = authService.refresh(new TokenRefreshRequest(raw));

        assertThat(principalOf(refreshed.accessToken()).qrSessionId()).isNull();
    }

    @Test
    @DisplayName("QR 세션 행이 삭제된 뒤 QR 토큰의 업로드와 파일 교체는 QR_002(410)로 명시적으로 거절된다")
    void deletedSessionRejectsUploadExplicitly() throws Exception {
        long existing = idOf(upload("/api/v1/worksheets", plainToken(owner), "files", 201));
        QrSession session = saveSession(owner.getId(), existing);
        String token = qrToken(owner, session);
        qrSessionRepository.deleteAll();

        String createBody = upload("/api/v1/worksheets", token, "files", 410);
        String replaceBody = upload("/api/v1/worksheets/" + existing + "/file", token, "file", 410, true);

        assertThat(createBody).contains("QR_002");
        assertThat(replaceBody).contains("QR_002");
        assertThat(worksheetRepository.count()).isEqualTo(1);

        // 일반 토큰은 영향이 없다
        upload("/api/v1/worksheets", plainToken(owner), "files", 201);
    }

    // ---------- helpers ----------

    private CustomUserPrincipal principalOf(String accessToken) {
        return (CustomUserPrincipal)
                jwtTokenProvider.getAuthentication(accessToken).getPrincipal();
    }

    private void saveRefresh(String raw, String qrSessionId) {
        refreshTokenRepository.save(RefreshToken.builder()
                .user(owner)
                .tokenHash(TokenHashUtil.hashToken(raw))
                .expiresAt(LocalDateTime.now().plusDays(14))
                .qrSessionId(qrSessionId)
                .build());
    }

    private String upload(String url, String authorization, String part, int expectedStatus) throws Exception {
        return upload(url, authorization, part, expectedStatus, false);
    }

    private String upload(String url, String authorization, String part, int expectedStatus, boolean put)
            throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(part, "photo.png", MediaType.IMAGE_PNG_VALUE, TestWorksheetImages.png(2, 3));
        MockMultipartHttpServletRequestBuilder builder = multipart(url).file(file);
        if (put) {
            builder.with(request -> {
                request.setMethod("PUT");
                return request;
            });
        }
        return mockMvc.perform(builder.header("Authorization", authorization))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private long idOf(String body) throws Exception {
        return com.jayway.jsonpath.JsonPath.<Number>read(body, "$.result.id").longValue();
    }

    private String dataLine(SseClient client) {
        return client.lines.stream()
                .filter(l -> l.startsWith("data:"))
                .findFirst()
                .orElseThrow();
    }

    private String plainToken(User user) {
        return "Bearer "
                + jwtTokenProvider.createAccessToken(user.getId(), academy.getId(), user.getEmail(), "TEACHER");
    }

    private String qrToken(User user, QrSession session) {
        return "Bearer "
                + jwtTokenProvider.createAccessToken(
                        user.getId(), academy.getId(), user.getEmail(), "TEACHER", session.getSessionId());
    }

    private QrSession saveSession(Long userId, Long worksheetId) {
        return qrSessionRepository.save(QrSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .qrToken(TokenHashUtil.generateRawToken())
                .userId(userId)
                .worksheetId(worksheetId)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build());
    }

    private User newUser(String prefix) {
        return userRepository.save(User.builder()
                .academy(academy)
                .email(prefix + "-" + UUID.randomUUID() + "@autedi.com")
                .passwordHash(passwordEncoder.encode("pw-1234!"))
                .name(prefix)
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private SseClient connect(User user) throws Exception {
        SseClient client =
                new SseClient(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/events"))
                        .header("Accept", "text/event-stream")
                        .header("Authorization", plainToken(user))
                        .GET()
                        .build());
        clients.add(client);
        client.start();
        await(() -> client.lines.contains(": connected"));
        return client;
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("조건이 시간 안에 충족되지 않았습니다.");
    }

    private final class SseClient {
        private final HttpRequest request;
        final List<String> lines = new CopyOnWriteArrayList<>();
        private volatile HttpResponse<java.io.InputStream> response;

        SseClient(HttpRequest request) {
            this.request = request;
        }

        void start() {
            Thread thread = new Thread(() -> {
                try {
                    response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    try (BufferedReader reader =
                            new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.isEmpty()) {
                                lines.add(line);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // 테스트 종료 시 연결 닫힘
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        void close() {
            try {
                if (response != null) {
                    response.body().close();
                }
            } catch (Exception ignored) {
                // 무시
            }
        }
    }
}
