package com.ceos24.autedi.global.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.auth.entity.QrSession;
import com.ceos24.autedi.domain.auth.event.QrSessionScanned;
import com.ceos24.autedi.domain.auth.repository.QrSessionRepository;
import com.ceos24.autedi.domain.auth.service.QrSessionService;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.ceos24.autedi.global.util.TokenHashUtil;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 서블릿 컨테이너를 띄워 SSE 연결, 인증, 이벤트 수신, 비동기 디스패치, 연결 정리를 검증한다.
 * 타이밍은 sleep 대신 조건 폴링과 하트비트 직접 호출로 처리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class SseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SseEmitterRegistry registry;

    @Autowired
    private SseHeartbeatScheduler heartbeat;

    @Autowired
    private QrSessionService qrSessionService;

    @Autowired
    private QrSessionRepository qrSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademyRepository academyRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final HttpClient http =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final List<SseClient> clients = new CopyOnWriteArrayList<>();

    private Academy academy;
    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        registry.snapshotAll().forEach(uc -> registry.close(uc.userId(), uc.connection()));
        academy = academyRepository.save(Academy.builder()
                .name("SSE 테스트 학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(1))
                .build());
        owner = newUser("owner");
        other = newUser("other");
    }

    @AfterEach
    void tearDown() {
        clients.forEach(SseClient::close);
        qrSessionRepository.deleteAll();
        userRepository.deleteAll();
        academyRepository.deleteAll();
    }

    @Test
    @DisplayName("인증 없는 연결, 잘못된 토큰은 401로 거부된다")
    void unauthenticated() throws Exception {
        assertThat(statusOf(null)).isEqualTo(401);
        assertThat(statusOf("Bearer not-a-token")).isEqualTo(401);
        assertThat(registry.totalCount()).isZero();
    }

    @Test
    @DisplayName("연결하면 초기 주석을 받고 SSE 응답 헤더가 설정된다")
    void connectAndHeaders() throws Exception {
        SseClient client = connect(owner);

        assertThat(client.status).isEqualTo(200);
        assertThat(client.header("content-type")).startsWith("text/event-stream");
        assertThat(client.header("cache-control")).contains("no-cache");
        assertThat(client.header("x-accel-buffering")).isEqualTo("no");
        await(() -> client.lines.contains(": connected"));
        assertThat(registry.count(owner.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("하트비트로 ping 주석을 받는다")
    void heartbeatPing() throws Exception {
        SseClient client = connect(owner);

        heartbeat.runOnce();

        await(() -> client.lines.contains(": ping"));
    }

    @Test
    @DisplayName("QR scan 후 qr.scanned를 수신하고, 다른 사용자는 받지 않으며, qrToken은 포함되지 않는다")
    void qrScannedDelivered() throws Exception {
        SseClient mine = connect(owner);
        SseClient others = connect(other);
        QrSession session = saveSession(owner.getId());

        qrSessionService.scan(session.getQrToken(), "iPhone");

        await(() -> mine.lines.stream().anyMatch(l -> l.startsWith("event:")));
        assertThat(mine.lines).contains("event:qr.scanned");
        String data = mine.lines.stream()
                .filter(l -> l.startsWith("data:"))
                .findFirst()
                .orElseThrow();
        assertThat(data).contains("\"sessionId\":\"" + session.getSessionId() + "\"");
        assertThat(data).contains("\"deviceInfo\":\"iPhone\"");
        assertThat(data).contains("\"eventId\"").contains("\"occurredAt\"");
        assertThat(String.join("\n", mine.lines)).doesNotContain(session.getQrToken());
        assertThat(mine.lines.stream().anyMatch(l -> l.startsWith("id:"))).isTrue();

        // 다른 사용자에게는 이벤트가 가지 않는다: 이후 ping이 도착했는데도 event 줄이 없어야 한다.
        heartbeat.runOnce();
        await(() -> others.lines.contains(": ping"));
        assertThat(others.lines.stream().anyMatch(l -> l.startsWith("event:"))).isFalse();
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 이벤트는 전송되지 않는다")
    void rollbackNotDelivered() throws Exception {
        SseClient mine = connect(owner);

        tx.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new QrSessionScanned(owner.getId(), "rolled-back", "iPhone"));
            status.setRollbackOnly();
        });
        heartbeat.runOnce();

        await(() -> mine.lines.contains(": ping"));
        assertThat(mine.lines.stream().anyMatch(l -> l.startsWith("event:"))).isFalse();
    }

    @Test
    @DisplayName("커밋된 트랜잭션의 이벤트는 커밋 이후에 전송된다")
    void commitDelivered() throws Exception {
        SseClient mine = connect(owner);

        tx.executeWithoutResult(
                status -> eventPublisher.publishEvent(new QrSessionScanned(owner.getId(), "committed", "Android")));

        await(() -> mine.lines.contains("event:qr.scanned"));
    }

    @Test
    @DisplayName("클라이언트가 끊기면 하트비트 시점에 레지스트리에서 제거된다")
    void disconnectCleansUp() throws Exception {
        SseClient client = connect(owner);
        assertThat(registry.count(owner.getId())).isEqualTo(1);

        client.close();

        await(() -> {
            heartbeat.runOnce();
            return registry.count(owner.getId()) == 0;
        });
    }

    @Test
    @DisplayName("사용자당 최대 연결 수를 넘으면 가장 오래된 연결이 닫힌다")
    void maxConnections() throws Exception {
        List<SseClient> opened = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            opened.add(connect(owner));
        }

        await(() -> registry.count(owner.getId()) == 5);
        await(() -> opened.get(0).ended);
        assertThat(opened.get(5).ended).isFalse();
    }

    @Test
    @DisplayName("액세스 토큰이 만료되면 하트비트 시점에 연결이 정상 종료되고, 비동기 디스패치에서 보안 오류가 없다")
    void expiredTokenClosesConnection(CapturedOutput output) throws Exception {
        JwtTokenProvider shortLived = new JwtTokenProvider(jwtSecret, 1_000);
        String token = shortLived.createAccessToken(owner.getId(), academy.getId(), owner.getEmail(), "TEACHER");
        SseClient client = connect("Bearer " + token);
        assertThat(registry.count(owner.getId())).isEqualTo(1);

        await(() -> {
            heartbeat.runOnce();
            return client.ended;
        });

        assertThat(registry.count(owner.getId())).isZero();
        assertThat(client.error).isNull();
        assertThat(output.getAll())
                .doesNotContain("AccessDeniedException")
                .doesNotContain("Unable to handle the Spring Security Exception")
                .doesNotContain("AuthorizationDeniedException");
    }

    @Test
    @DisplayName("서버가 정상 종료한 연결의 비동기 디스패치도 401/403 없이 끝난다 (만료 전 연결을 직접 닫기)")
    void asyncDispatchAfterServerClose(CapturedOutput output) throws Exception {
        SseClient client = connect(owner);

        registry.connectionsOf(owner.getId()).forEach(c -> registry.close(owner.getId(), c));

        await(() -> client.ended);
        assertThat(client.error).isNull();
        assertThat(output.getAll())
                .doesNotContain("AccessDeniedException")
                .doesNotContain("Unable to handle the Spring Security Exception")
                .doesNotContain("AuthorizationDeniedException");
    }

    // ---------- helpers ----------

    private int statusOf(String authorization) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/events"))
                .header("Accept", "text/event-stream")
                .GET();
        if (authorization != null) {
            b.header("Authorization", authorization);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private SseClient connect(User user) throws Exception {
        String token = jwtTokenProvider.createAccessToken(user.getId(), academy.getId(), user.getEmail(), "TEACHER");
        return connect("Bearer " + token);
    }

    private SseClient connect(String authorization) throws Exception {
        SseClient client =
                new SseClient(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/events"))
                        .header("Accept", "text/event-stream")
                        .header("Authorization", authorization)
                        .GET()
                        .build());
        clients.add(client);
        client.start();
        try {
            await(() -> client.status != 0);
            if (client.status == 200) {
                await(() -> client.lines.contains(": connected"));
            }
        } catch (AssertionError e) {
            throw new AssertionError(
                    "연결 대기 실패: status=" + client.status + ", lines=" + client.lines + ", error=" + client.error, e);
        }
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

    private QrSession saveSession(Long userId) {
        return qrSessionRepository.save(QrSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .qrToken(TokenHashUtil.generateRawToken())
                .userId(userId)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build());
    }

    /** 응답 본문을 줄 단위로 읽어 모으는 SSE 클라이언트. */
    private final class SseClient {
        private final HttpRequest request;
        final List<String> lines = new CopyOnWriteArrayList<>();
        volatile int status;
        volatile boolean ended;
        volatile Throwable error;
        private volatile HttpResponse<java.io.InputStream> response;
        private Thread thread;

        SseClient(HttpRequest request) {
            this.request = request;
        }

        void start() {
            thread = new Thread(() -> {
                try {
                    response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    status = response.statusCode();
                    try (BufferedReader reader =
                            new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.isEmpty()) {
                                lines.add(line);
                            }
                        }
                    }
                    ended = true;
                } catch (Exception e) {
                    if (status == 0) {
                        status = -1;
                    }
                    // 클라이언트가 직접 닫은 경우는 오류로 보지 않는다.
                    if (!closedByClient) {
                        error = e;
                    }
                    ended = true;
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        private volatile boolean closedByClient;

        String header(String name) {
            return response.headers().firstValue(name).orElse("");
        }

        void close() {
            closedByClient = true;
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
