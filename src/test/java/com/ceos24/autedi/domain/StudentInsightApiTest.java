package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.comment.entity.*;
import com.ceos24.autedi.domain.comment.repository.CommentRepository;
import com.ceos24.autedi.domain.correction.entity.*;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.correction.service.CorrectionService;
import com.ceos24.autedi.domain.generation.entity.*;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.generation.service.AiExecutionLimiter;
import com.ceos24.autedi.domain.insight.client.InsightClient;
import com.ceos24.autedi.domain.insight.repository.StudentInsightRepository;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.*;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.*;
import com.ceos24.autedi.domain.worksheet.repository.*;
import com.ceos24.autedi.domain.worksheet.service.WorksheetSavedSnapshot;
import com.ceos24.autedi.global.exception.*;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:insight_api;DB_CLOSE_DELAY=-1;MODE=MySQL",
            "app.generation.worker-count=2",
            "spring.jpa.properties.hibernate.generate_statistics=true"
        })
class StudentInsightApiTest {
    @Autowired
    WebApplicationContext context;

    @Autowired
    AcademyRepository academies;

    @Autowired
    UserRepository users;

    @Autowired
    StudentRepository students;

    @Autowired
    WorksheetRepository worksheets;

    @Autowired
    WorksheetPageRepository pages;

    @Autowired
    WorksheetGenerationRepository generations;

    @Autowired
    CorrectionRepository corrections;

    @Autowired
    CommentRepository comments;

    @Autowired
    WorksheetSavedResultRepository snapshots;

    @Autowired
    StudentInsightRepository insights;

    @Autowired
    CorrectionService reviews;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    EntityManager em;

    @Autowired
    EntityManagerFactory emf;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AiExecutionLimiter limiter;

    @MockitoBean
    InsightClient client;

    private final JsonMapper json = JsonMapper.builder().build();
    private TransactionTemplate tx;
    private MockMvc mvc;
    private User owner, peer, director, foreign;
    private Student student;
    private final List<Long> worksheetIds = new ArrayList<>();
    private String lastInput;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        tx = new TransactionTemplate(manager);
        worksheetIds.clear();
        lastInput = null;
        tx.executeWithoutResult(s -> {
            var academy =
                    academies.saveAndFlush(Academy.builder().name("insight").build());
            owner = user(academy, Role.TEACHER);
            peer = user(academy, Role.TEACHER);
            director = user(academy, Role.DIRECTOR);
            foreign =
                    user(academies.saveAndFlush(Academy.builder().name("other").build()), Role.TEACHER);
            student = students.saveAndFlush(Student.builder()
                    .academy(academy)
                    .name("private-student-name")
                    .grade("초3")
                    .guardianPhone("01012345678")
                    .build());
            for (int i = 0; i < 3; i++) completed(i, GenerationClientMode.TERRA, true);
        });
        doAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    lastInput = call.getArgument(0);
                    return answer(lastInput);
                })
                .when(client)
                .generate(anyString());
    }

    @Test
    void manualGenerationReturnsScreenArraysAndGetAndIdenticalPostReuseWithoutLlm() throws Exception {
        read(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("NOT_GENERATED"));
        verifyNoInteractions(client);
        var first = result(generate(owner).andExpect(status().isOk()));
        assertThat(first.path("status").asText()).isEqualTo("READY");
        assertThat(first.path("insights").size()).isEqualTo(1);
        assertThat(first.path("coachingPoints").size()).isEqualTo(2);
        assertThat(first.path("isStale").asBoolean()).isFalse();
        assertThat(first.path("usedResultCount").asInt()).isEqualTo(3);
        assertThat(first.path("evidence").size()).isEqualTo(2);
        assertThat(lastInput)
                .doesNotContain(
                        "private-student-name", "01012345678", "preview-secret", "base64", "regions", "image_url");
        assertThat(json.readTree(lastInput).at("/results/0/errorCount").asInt()).isEqualTo(2);
        var repeated = result(generate(peer).andExpect(status().isOk()));
        assertThat(repeated.path("reused").asBoolean()).isTrue();
        assertThat(repeated.path("generatedAt")).isEqualTo(first.path("generatedAt"));
        read(director)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("READY"));
        verify(client, times(1)).generate(anyString());
        assertThat(insights.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void newCompletionMarksCacheStaleWithoutCallingAiAndManualUpdateReplacesOneRow() throws Exception {
        var first = result(generate(owner).andExpect(status().isOk()));
        tx.executeWithoutResult(s -> completed(0, GenerationClientMode.TERRA, true));
        read(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isStale").value(true))
                .andExpect(jsonPath("$.result.candidateResultCount").value(4));
        verify(client, times(1)).generate(anyString());
        var updated = result(generate(peer).andExpect(status().isOk()));
        assertThat(updated.path("isStale").asBoolean()).isFalse();
        assertThat(updated.path("usedResultCount").asInt()).isEqualTo(4);
        assertThat(updated.path("generatedAt")).isNotEqualTo(first.path("generatedAt"));
        verify(client, times(2)).generate(anyString());
    }

    @Test
    void failureKeepsPreviousSuccessfulResultAndReleasesExecutionPermit() throws Exception {
        var first = result(generate(owner).andExpect(status().isOk()));
        tx.executeWithoutResult(s -> completed(0, GenerationClientMode.TERRA, true));
        doThrow(new CustomException(ErrorCode.INSIGHT_GENERATION_FAILED))
                .when(client)
                .generate(anyString());
        generate(owner)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INSIGHT_003"));
        var old = result(read(owner).andExpect(status().isOk()));
        assertThat(old.path("generatedAt")).isEqualTo(first.path("generatedAt"));
        assertThat(old.path("isStale").asBoolean()).isTrue();
        try (var permit = limiter.tryInsight()) {
            assertThat(permit).isNotNull();
        }
    }

    @Test
    void busyServerRejectsWithoutMaterializingInputOrCallingAiButFreshCacheStillWorks() throws Exception {
        try (var one = limiter.tryCorrection();
                var two = limiter.tryCorrection()) {
            generate(owner)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("INSIGHT_001"));
            verifyNoInteractions(client);
        }
        generate(owner).andExpect(status().isOk());
        try (var one = limiter.tryCorrection();
                var two = limiter.tryCorrection()) {
            generate(owner)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.reused").value(true));
        }
        verify(client, times(1)).generate(anyString());
    }

    @Test
    void concurrentGenerationRunsOnceAndSharesBudgetWithCorrections() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
                    started.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("timeout");
                    return answer(call.getArgument(0));
                })
                .when(client)
                .generate(anyString());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> generate(owner).andExpect(status().isOk()));
            try {
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                generate(peer).andExpect(status().isServiceUnavailable());
                try (var correction = limiter.tryCorrection()) {
                    assertThat(correction).isNotNull();
                    assertThat(limiter.tryCorrection()).isNull();
                }
                verify(client, times(1)).generate(anyString());
            } finally {
                release.countDown();
            }
            pending.get(10, TimeUnit.SECONDS);
        }
        generate(peer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reused").value(true));
    }

    @Test
    void inputChangeDuringAiCallDoesNotPublishAnOutdatedResult() throws Exception {
        doAnswer(call -> {
                    tx.executeWithoutResult(s -> completed(0, GenerationClientMode.TERRA, true));
                    return answer(call.getArgument(0));
                })
                .when(client)
                .generate(anyString());
        generate(owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSIGHT_005"));
        assertThat(insights.findById(student.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void insufficientDataNeverCallsAi(int count) throws Exception {
        for (int i = count; i < 3; i++)
            jdbc.update("update worksheets set save_status='TEMP_SAVED' where id=?", worksheetIds.get(i));
        generate(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value(count == 0 ? "EMPTY" : "INSUFFICIENT_DATA"));
        verifyNoInteractions(client);
    }

    @Test
    void noConfirmedFeedbackDoesNotInventErrorsOrCallAi() throws Exception {
        tx.executeWithoutResult(s -> {
            for (Long id : worksheetIds) {
                corrections.findAllByWorksheetIdOrderByIdAsc(id).forEach(Correction::delete);
                comments.findAllByWorksheetIdOrderByIdAsc(id).forEach(Comment::delete);
                freeze(worksheets.findById(id).orElseThrow());
            }
        });
        generate(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("NO_FEEDBACK"));
        generate(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reused").value(true));
        verifyNoInteractions(client);
    }

    @Test
    void corruptedSnapshotIsExcludedAndDoesNotSatisfyMinimum() throws Exception {
        jdbc.update(
                "update worksheet_saved_results set schema_version=999 where worksheet_id=?", worksheetIds.getFirst());
        generate(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.result.candidateResultCount").value(3))
                .andExpect(jsonPath("$.result.usedResultCount").value(2));
        verifyNoInteractions(client);
    }

    @Test
    void analysisWindowAndMaximumAreAppliedBeforeSnapshotLoadingAndMockDataIsExcluded() throws Exception {
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 11; i++) completed(i, GenerationClientMode.TERRA, true);
            completed(120, GenerationClientMode.TERRA, true);
            completed(0, GenerationClientMode.FIXED, false);
        });
        generate(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.candidateResultCount").value(10));
        assertThat(json.readTree(lastInput).path("results").size()).isEqualTo(10);
        assertThat(json.readTree(lastInput).path("evidence").size()).isLessThanOrEqualTo(60);
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        read(owner).andExpect(status().isOk());
        assertThat(stats.getEntityStatistics(WorksheetSavedResult.class.getName())
                        .getLoadCount())
                .isZero();
    }

    @Test
    void missingForeignStudentAndAuthenticationAreEnforced() throws Exception {
        read(foreign)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"));
        generate(foreign).andExpect(status().isNotFound());
        call(get("/api/v1/students/999999/insights"), owner).andExpect(status().isNotFound());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(post(path() + "/generations")).andExpect(status().isUnauthorized());
        verifyNoInteractions(client);
    }

    @Test
    void invalidEvidenceAndUnjustifiedRepeatedPatternAreRejected() throws Exception {
        doReturn("{\"insights\":[],\"coachingPoints\":[{\"content\":\"지도\",\"evidenceIds\":[\"invented\"]}]}")
                .when(client)
                .generate(anyString());
        generate(owner)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INSIGHT_004"));
        assertThat(insights.findById(student.getId())).isEmpty();
    }

    private Long completed(int daysAgo, GenerationClientMode mode, boolean feedback) {
        var w = worksheets.saveAndFlush(Worksheet.builder()
                .academy(owner.getAcademy())
                .user(owner)
                .originalFilename("private-name.png")
                .fileUrl("/private-original")
                .fileType(FileType.PNG)
                .fileSize(10L)
                .fileHash(UUID.randomUUID().toString())
                .build());
        w.updateMetadata("학습지", student);
        w.updateStatus(WorksheetStatus.READY);
        var p = pages.saveAndFlush(WorksheetPage.builder()
                .worksheet(w)
                .pageNumber(1)
                .previewImageUrl("/preview-secret")
                .width(100)
                .height(100)
                .build());
        var g = generations.saveAndFlush(WorksheetGeneration.builder()
                .worksheet(w)
                .requestedBy(owner)
                .clientMode(mode)
                .idempotencyKey(UUID.randomUUID().toString())
                .requestFileHash(w.getFileHash())
                .requestSettingsSnapshot(Map.of())
                .build());
        g.succeed();
        if (feedback) {
            corrections.saveAndFlush(Correction.builder()
                    .worksheet(w)
                    .page(p)
                    .generation(g)
                    .area(CorrectionArea.GRAMMAR_SPELLING)
                    .originalText("He go to school.")
                    .aiInitialContent("He goes to school.")
                    .explanation("3인칭 단수 주어와 동사")
                    .regions(List.of(new RegionPayload(p.getId(), .1, .1, .1, .1, null)))
                    .build());
            var c = Comment.builder()
                    .worksheet(w)
                    .page(p)
                    .user(owner)
                    .highlightType(HighlightType.HIGHLIGHT)
                    .regions(List.of(new RegionPayload(p.getId(), .2, .2, .1, .1, null)))
                    .content("주어에 맞는 동사 형태를 확인하세요.")
                    .build();
            c.changeClassification(new CommentClassification(
                    CommentFeedbackType.CORRECTION,
                    CorrectionArea.GRAMMAR_SPELLING,
                    List.of(com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind.GRAMMAR),
                    null));
            comments.saveAndFlush(c);
        }
        w.complete(
                owner,
                LocalDate.now(ZoneId.of("Asia/Seoul"))
                        .minusDays(daysAgo)
                        .atTime(12, 0)
                        .truncatedTo(ChronoUnit.MICROS));
        em.flush();
        freeze(w);
        worksheetIds.add(w.getId());
        return w.getId();
    }

    private void freeze(Worksheet w) {
        var g = generations
                .findFirstByWorksheetIdOrderByCreatedAtDescIdDesc(w.getId())
                .orElseThrow();
        var snap = new WorksheetSavedSnapshot(
                reviews.getReview(owner.getAcademy().getId(), w.getId()),
                corrections.findAllByWorksheetIdOrderByIdAsc(w.getId()).stream()
                        .map(WorksheetSavedSnapshot.CorrectionEdit::from)
                        .toList(),
                comments.findAllByWorksheetIdOrderByIdAsc(w.getId()).stream()
                        .map(WorksheetSavedSnapshot.CommentEdit::from)
                        .toList());
        var row = snapshots.findById(w.getId()).orElseGet(() -> new WorksheetSavedResult(w));
        row.replace(g, owner, w.getCompletedAt(), json.convertValue(snap, new TypeReference<Map<String, Object>>() {}));
        snapshots.saveAndFlush(row);
    }

    private String answer(String input) {
        var evidence = json.readTree(input).path("evidence");
        String one = evidence.get(0).path("id").asText();
        long worksheet = evidence.get(0).path("worksheetId").asLong();
        String two = evidence.valueStream()
                .filter(e -> e.path("worksheetId").asLong() != worksheet)
                .findFirst()
                .orElseThrow()
                .path("id")
                .asText();
        return json.writeValueAsString(Map.of(
                "insights",
                List.of(Map.of(
                        "title",
                        "동사 형태 확인",
                        "description",
                        "여러 결과물에서 주어에 맞는 동사 형태 피드백이 반복됩니다.",
                        "area",
                        "GRAMMAR_SPELLING",
                        "kind",
                        "ERROR_PATTERN",
                        "evidenceIds",
                        List.of(one, two))),
                "coachingPoints",
                List.of(
                        Map.of("content", "주어를 바꾸며 동사를 비교해 보세요.", "evidenceIds", List.of(one)),
                        Map.of("content", "수정한 문장을 다시 써 보세요.", "evidenceIds", List.of(two)))));
    }

    private User user(Academy a, Role role) {
        return users.saveAndFlush(User.builder()
                .academy(a)
                .role(role)
                .name("teacher")
                .email(UUID.randomUUID() + "@test.com")
                .passwordHash("hash")
                .build());
    }

    private String path() {
        return "/api/v1/students/" + student.getId() + "/insights";
    }

    private ResultActions read(User u) throws Exception {
        return call(get(path()), u);
    }

    private ResultActions generate(User u) throws Exception {
        return call(post(path() + "/generations"), u);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, User u) throws Exception {
        return mvc.perform(request.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer "
                        + tokens.createAccessToken(
                                u.getId(),
                                u.getAcademy().getId(),
                                u.getEmail(),
                                u.getRole().name())));
    }

    private JsonNode result(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsByteArray())
                .path("result");
    }
}
