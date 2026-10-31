package com.ceos24.autedi.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.generation.client.CorrectionAiClient;
import com.ceos24.autedi.domain.generation.client.TerraClientProperties;
import com.ceos24.autedi.domain.generation.client.TerraGenerationClient;
import com.ceos24.autedi.domain.generation.contract.v1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1.prompt.AiCorrectionPromptComposer;
import com.ceos24.autedi.domain.generation.entity.*;
import com.ceos24.autedi.domain.generation.persistence.StoredTranscription;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.generation.service.*;
import com.ceos24.autedi.domain.generation.service.GenerationResultStorageService;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.ceos24.autedi.global.storage.LocalStorageService;
import com.ceos24.autedi.support.FakeResponsesServer;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.io.ByteArrayInputStream;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 MVC·보안·커밋된 H2를 통과한다. 클라이언트를 대체하고 어떤 AI 클라이언트 호출도 없음을 확인한다. */
@ActiveProfiles("test")
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:fixed_review_api;DB_CLOSE_DELAY=-1;MODE=MySQL",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.ai.fixed-response.enabled=true",
            "app.ai.client=FIXED",
            "app.ai.terra.api-key=test-only-value",
            "app.ai.terra.endpoint=http://127.0.0.1:1/v1/responses"
        })
class FixedResponseReviewApiTest {
    @TempDir
    static Path uploadRoot;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.upload-dir", () -> uploadRoot.toString());
        registry.add("app.backend-url", () -> "http://fixed-test.invalid");
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    WebApplicationContext context;

    @Autowired
    AcademyRepository academies;

    @Autowired
    UserRepository users;

    @Autowired
    WorksheetRepository worksheets;

    @MockitoSpyBean
    WorksheetGenerationRepository generations;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    GenerationResultStorageService archives;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @MockitoBean
    CorrectionAiClient aiClient;

    @MockitoSpyBean
    FixedResponseGenerationService fixed;

    @Autowired
    GenerationWorkStore workStore;

    @Autowired
    GenerationWorker worker;

    @Autowired
    GenerationJobDispatcher dispatcher;

    @Autowired
    WorksheetGenerationService generationService;

    @Autowired
    WorksheetService images;

    @Autowired
    @Qualifier("generationExecutor")
    ThreadPoolTaskExecutor executor;

    @Autowired
    TerraClientProperties terraProperties;

    @MockitoSpyBean
    TerraGenerationClient terra;

    private MockMvc mvc;
    private Long academyId, worksheetId, userId;
    private String bearer, otherBearer;
    private boolean legacyUsed;

    @BeforeEach
    void setup() throws Exception {
        ReflectionTestUtils.setField(generationService, "configuredClient", null);
        localDatabase();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Academy academy = academy();
            academyId = academy.getId();
            User teacher = user(academy);
            userId = teacher.getId();
            bearer = bearer(teacher);
            otherBearer = bearer(user(academy()));
        });
        worksheetId = body(mvc.perform(multipart("/api/v1/worksheets")
                                .file(new MockMultipartFile(
                                        "file", "page.png", "image/png", TestWorksheetImages.png(2, 3)))
                                .header(HttpHeaders.AUTHORIZATION, bearer))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .longValue();
        configure(true);
    }

    @AfterEach
    void cleanup() throws Exception {
        awaitIdle();
        ReflectionTestUtils.setField(generationService, "configuredClient", null);
        terraProperties.setEndpoint(java.net.URI.create("http://127.0.0.1:1/v1/responses"));
        ReflectionTestUtils.setField(fixed, "failAt", "NONE");
        if (!legacyUsed) verifyNoInteractions(aiClient);
        localDatabase();
        for (String table : List.of(
                "comments",
                "worksheet_generation_results",
                "corrections",
                "worksheet_transcriptions",
                "worksheet_generations",
                "worksheet_settings",
                "worksheet_pages",
                "worksheets",
                "users",
                "academies")) jdbc.update("delete from " + table);
    }

    @Test
    void fixedGenerationValidatesStoresAndQueriesCardsReviewIssuesAndWarnings() throws Exception {
        JsonNode generation = generate("fixed-first");
        assertEquals("SUCCEEDED", generation.get("status").asString());
        JsonNode review = review();
        assertEquals(4, review.get("corrections").size());
        assertEquals(3, review.get("reviewIssues").size());
        assertEquals(1, review.get("aiResults").size());
        assertEquals("FIXED", review.at("/aiResults/0/source").asString());
        assertEquals("fixed-response", review.at("/aiResults/0/metadata/model").asString());
        assertEquals(1, omitted(review));
        assertTrue(review.get("reviewIssues")
                .valueStream()
                .anyMatch(
                        issue -> issue.get("original").get("action").asString().equals("REVIEW")));
        assertTrue(review.get("reviewIssues").valueStream().anyMatch(issue -> hasWarning(issue, "ANCHOR_NOT_FOUND")));
        for (JsonNode card : review.get("corrections")) {
            assertEquals("FIXED", card.get("source").asString());
            assertEquals("NORMALIZED_0_1", card.get("regionCoordinateUnit").asString());
            assertTrue(card.at("/anchor/transcriptionId").longValue() > 0);
        }
        JsonNode guidance = card(review, AiAction.GUIDANCE);
        assertEquals(
                "Add one reason you like this book.",
                guidance.get("aiInitialContent").asString());
        JsonNode cards = result(get(base() + "/corrections").param("status", "ALL"));
        assertEquals(4, cards.get("total").intValue());
        JsonNode detail =
                result(get(base() + "/corrections/" + guidance.get("id").longValue()));
        assertEquals("GUIDANCE", detail.get("action").asString());
        assertEquals(guidance.get("aiInitialContent"), detail.get("content"));
        assertTrue(review.get("corrections")
                .valueStream()
                .anyMatch(item -> hasWarning(item, "INVALID_BOX")
                        && item.get("requiresReview").asBoolean()));
        assertEquals(1, count("worksheet_transcriptions"));
        assertEquals(1, count("worksheet_generation_results"));
    }

    @ParameterizedTest
    @EnumSource(
            value = AiAction.class,
            names = {"REPLACE", "GUIDANCE"})
    void teacherEditDeleteRestorePreserveFirstAiContentAnchorWarningsAndArchive(AiAction action) throws Exception {
        generate("lifecycle");
        JsonNode firstReview = review();
        JsonNode initial = card(firstReview, action);
        long correctionId = initial.get("id").longValue();
        long archiveId = firstReview.at("/aiResults/0/id").longValue();
        var archived = archives.restoreResult(academyId, archiveId);
        String url = base() + "/corrections/" + correctionId;
        String edited = action == AiAction.REPLACE ? "  teacher replacement  " : "Add a supporting example.";
        MvcResult patchResult = mvc.perform(patch(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header(HttpHeaders.IF_MATCH, initial.get("etag").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("content", edited))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.editedByTeacher").value(true))
                .andReturn();
        JsonNode patched = body(patchResult);
        assertEquals(edited, patched.get("content").asString());
        assertPreserved(initial, patched);
        JsonNode sameEdit = result(patch(url)
                .header(HttpHeaders.IF_MATCH, patched.get("etag").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("content", edited))));
        assertEquals(patched.get("etag"), sameEdit.get("etag"));
        MvcResult deleted = mvc.perform(delete(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header(HttpHeaders.IF_MATCH, sameEdit.get("etag").asString()))
                .andExpect(status().isNoContent())
                .andReturn();
        JsonNode afterDelete = review();
        assertEquals(3, afterDelete.get("corrections").size());
        assertEquals(
                3,
                afterDelete
                        .get("summary")
                        .valueStream()
                        .mapToInt(area -> area.get("displayedCount").intValue())
                        .sum());
        assertEquals(firstReview.get("reviewIssues"), afterDelete.get("reviewIssues"));
        assertEquals(
                1,
                result(get(base() + "/corrections").param("status", "DELETED"))
                        .get("total")
                        .intValue());
        mvc.perform(patch(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header(HttpHeaders.IF_MATCH, deleted.getResponse().getHeader(HttpHeaders.ETAG))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"blocked\"}"))
                .andExpect(status().isConflict());
        JsonNode restored = result(post(url + "/restore")
                .header(HttpHeaders.IF_MATCH, deleted.getResponse().getHeader(HttpHeaders.ETAG)));
        assertEquals("ACTIVE", restored.get("status").asString());
        assertEquals(edited, restored.get("content").asString());
        assertPreserved(initial, restored);
        assertEquals(4, review().get("corrections").size());
        assertEquals(archived, archives.restoreResult(academyId, archiveId));
    }

    @Test
    void emptyReplacementMeansDeletionButGuidanceAndNullEditsAreRejected() throws Exception {
        generate("edit-validation");
        JsonNode initial = review();
        JsonNode replacement = card(initial, AiAction.REPLACE);
        JsonNode guidance = card(initial, AiAction.GUIDANCE);
        String replaceUrl = base() + "/corrections/" + replacement.get("id").longValue();
        String guidanceUrl = base() + "/corrections/" + guidance.get("id").longValue();
        JsonNode deletion = result(patch(replaceUrl)
                .header(HttpHeaders.IF_MATCH, replacement.get("etag").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"\"}"));
        assertEquals("", deletion.get("content").asString());
        assertPreserved(replacement, deletion);
        for (String body : List.of("{\"content\":\"\"}", "{\"content\":\"   \"}", "{\"content\":null}")) {
            mvc.perform(patch(guidanceUrl)
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .header(HttpHeaders.IF_MATCH, guidance.get("etag").asString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch(replaceUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header(HttpHeaders.IF_MATCH, replacement.get("etag").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"stale\"}"))
                .andExpect(status().isPreconditionFailed());
        assertEquals(guidance.get("content"), result(get(guidanceUrl)).get("content"));
    }

    @Test
    void repeatedGenerationDoesNotDuplicateAndOtherAcademyCannotUseItsIdempotencyKeyOrReadCards() throws Exception {
        JsonNode first = generate("one-generation");
        assertEquals(first.get("id"), generate("one-generation").get("id"));
        assertEquals(1, count("worksheet_generations"));
        assertEquals(1, count("worksheet_generation_results"));
        assertEquals(4, count("corrections"));
        long cardId = review().at("/corrections/0/id").longValue();
        mvc.perform(post(base() + "/generations")
                        .header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .header("Idempotency-Key", "one-generation"))
                .andExpect(status().isNotFound());
        mvc.perform(get(base() + "/review").header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound());
        mvc.perform(get(base() + "/corrections/" + cardId).header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void disabledContentAreaExcludesGuidanceAndReportsOmissions() throws Exception {
        configure(false);
        generate("grammar-only");
        JsonNode review = review();
        assertEquals(3, review.get("corrections").size());
        assertEquals(3, review.get("reviewIssues").size());
        assertEquals(2, omitted(review));
        assertTrue(review.get("corrections")
                .valueStream()
                .noneMatch(item -> item.get("action").asString().equals("GUIDANCE")));
        assertEquals(
                0,
                result(get(base() + "/corrections").param("area", "CONTENT_SUMMARY"))
                        .get("total")
                        .intValue());
    }

    @Test
    void retryShowsOnlyLatestReviewIssuesAndKeepsOldAuditAndCards() throws Exception {
        long first = generate("first-attempt").get("id").longValue();
        long firstArchive = review().at("/aiResults/0/id").longValue();
        var oldAudit = archives.restoreResult(academyId, firstArchive);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            generations.findById(first).orElseThrow().fail("LOCAL_TEST", "로컬 재시도 테스트", true);
            worksheets.findById(worksheetId).orElseThrow().updateStatus(WorksheetStatus.FAILED);
        });
        JsonNode retry = body(mvc.perform(post(base() + "/generations/" + first + "/retry")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header("Idempotency-Key", "second-attempt"))
                .andExpect(status().isAccepted())
                .andReturn());
        retry = awaitTerminal(retry.get("id").longValue());
        long retryId = retry.get("id").longValue();
        assertNotEquals(first, retry.get("id").longValue());
        JsonNode review = review();
        assertEquals(4, review.get("corrections").size());
        assertEquals(3, review.get("reviewIssues").size());
        assertTrue(review.get("reviewIssues")
                .valueStream()
                .allMatch(item -> item.get("generationId").longValue() == retryId));
        assertEquals(
                4,
                result(get(base() + "/corrections").param("status", "DELETED"))
                        .get("total")
                        .intValue());
        assertEquals(oldAudit, archives.restoreResult(academyId, firstArchive));
        assertEquals(2, count("worksheet_generation_results"));
    }

    @Test
    void committedRequestRunsInBackgroundAndExposesBothStagesWithoutHoldingATransaction() throws Exception {
        CountDownLatch ocrEntered = new CountDownLatch(1), releaseOcr = new CountDownLatch(1);
        CountDownLatch correctionEntered = new CountDownLatch(1), releaseCorrection = new CountDownLatch(1);
        AtomicBoolean ocrTransaction = new AtomicBoolean(true), correctionTransaction = new AtomicBoolean(true);
        AtomicReference<Object> ocrImage = new AtomicReference<>(), correctionImage = new AtomicReference<>();
        doAnswer(call -> {
                    ocrTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
                    ocrImage.set(call.getArgument(0));
                    ocrEntered.countDown();
                    if (!releaseOcr.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("OCR gate timeout");
                    return call.callRealMethod();
                })
                .when(fixed)
                .transcribe(any(), any());
        doAnswer(call -> {
                    correctionTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
                    correctionImage.set(call.getArgument(0));
                    correctionEntered.countDown();
                    if (!releaseCorrection.await(5, TimeUnit.SECONDS))
                        throw new IllegalStateException("correction gate timeout");
                    return call.callRealMethod();
                })
                .when(fixed)
                .correct(any(), any());
        long id;
        try {
            JsonNode accepted = body(mvc.perform(post(base() + "/generations")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .header("Idempotency-Key", "delayed"))
                    .andExpect(status().isAccepted())
                    .andReturn());
            assertEquals("PENDING", accepted.get("status").asString());
            id = accepted.get("id").longValue();
            assertTrue(ocrEntered.await(5, TimeUnit.SECONDS));
            assertFalse(ocrTransaction.get());
            JsonNode ocr = result(get(base() + "/generations/" + id));
            assertEquals("RUNNING", ocr.get("status").asString());
            assertEquals("OCR", ocr.get("stage").asString());
            assertEquals(1, count("worksheet_generations"));
            assertEquals(0, count("worksheet_transcriptions"));
            // 다른 커넥션에서 DB 행과 단계가 보이고, 중복 실행 요청도 클라이언트를 재호출하지 않는다.
            worker.process(id);
            JsonNode duplicate = body(mvc.perform(post(base() + "/generations")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .header("Idempotency-Key", "delayed"))
                    .andExpect(status().isAccepted())
                    .andReturn());
            assertEquals(id, duplicate.get("id").longValue());
            mvc.perform(post(base() + "/generations")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .header("Idempotency-Key", "another-call"))
                    .andExpect(status().isConflict());
            releaseOcr.countDown();
            assertTrue(correctionEntered.await(5, TimeUnit.SECONDS));
            assertFalse(correctionTransaction.get());
            assertSame(ocrImage.get(), correctionImage.get());
            assertEquals(
                    "AI_CORRECTION",
                    result(get(base() + "/generations/" + id)).get("stage").asString());
            assertEquals(1, count("worksheet_transcriptions"));
            assertEquals(0, count("corrections"));
        } finally {
            releaseOcr.countDown();
            releaseCorrection.countDown();
        }
        assertEquals("SUCCEEDED", awaitTerminal(id).get("status").asString());
        verify(fixed, times(1)).transcribe(any(), any());
        verify(fixed, times(1)).correct(any(), any());
        var image = images.getPreparedImage(academyId, worksheetId, 1);
        var audit =
                archives.restoreResult(academyId, review().at("/aiResults/0/id").longValue());
        assertEquals(image.sha256(), audit.transcription().sourceImageHash());
        assertEquals(image.width(), audit.transcription().imageWidth());
        assertEquals(image.height(), audit.transcription().imageHeight());
        assertEquals(
                "COMPLETED",
                result(get(base() + "/generations/" + id)).get("stage").asString());
    }

    @ParameterizedTest
    @EnumSource(
            value = GenerationStage.class,
            names = {"OCR", "AI_CORRECTION"})
    void stageFailurePreservesCommittedTranscriptionAndManualRetryIsIdempotent(GenerationStage stage) throws Exception {
        ReflectionTestUtils.setField(fixed, "failAt", stage.name());
        JsonNode failed = generate("failed-stage");
        assertEquals("FAILED", failed.get("status").asString());
        assertEquals(stage.name(), failed.get("stage").asString());
        assertEquals(stage.name() + "_FAILED", failed.at("/error/code").asString());
        assertTrue(failed.at("/error/retryable").asBoolean());
        assertEquals(stage == GenerationStage.OCR ? 0 : 1, count("worksheet_transcriptions"));
        assertEquals(0, count("worksheet_generation_results"));
        assertEquals(0, count("corrections"));
        Long transcriptionId = stage == GenerationStage.OCR
                ? null
                : jdbc.queryForObject("select id from worksheet_transcriptions", Long.class);
        var frozen = transcriptionId == null ? null : archives.restoreTranscription(academyId, transcriptionId);
        if (stage == GenerationStage.AI_CORRECTION) {
            configure(false);
            mvc.perform(post(base() + "/generations/" + failed.get("id").longValue() + "/retry")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .header("Idempotency-Key", "changed-settings"))
                    .andExpect(status().isConflict());
            configure(true);
        }
        ReflectionTestUtils.setField(fixed, "failAt", "NONE");
        String retryUrl = base() + "/generations/" + failed.get("id").longValue() + "/retry";
        JsonNode accepted = body(mvc.perform(post(retryUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header("Idempotency-Key", "manual-retry"))
                .andExpect(status().isAccepted())
                .andReturn());
        JsonNode succeeded = awaitTerminal(accepted.get("id").longValue());
        assertEquals("SUCCEEDED", succeeded.get("status").asString());
        assertEquals(2, succeeded.get("attempt").intValue());
        JsonNode duplicate = body(mvc.perform(post(retryUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header("Idempotency-Key", "manual-retry"))
                .andExpect(status().isAccepted())
                .andReturn());
        assertEquals(succeeded.get("id"), duplicate.get("id"));
        assertEquals(2, count("worksheet_generations"));
        if (frozen != null) {
            assertEquals(frozen, archives.restoreTranscription(academyId, transcriptionId));
            assertEquals(1, count("worksheet_transcriptions"));
            assertEquals(
                    transcriptionId,
                    generations
                            .findById(succeeded.get("id").longValue())
                            .orElseThrow()
                            .getInputTranscriptionId());
        }
        verify(fixed, times(stage == GenerationStage.OCR ? 2 : 1)).transcribe(any(), any());
        assertEquals(
                "FAILED",
                result(get(base() + "/generations/" + failed.get("id").longValue()))
                        .get("status")
                        .asString());
    }

    @Test
    void rolledBackAcceptanceDoesNotStartAClientOrLeaveAJob() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            var accepted = generationService.requestGeneration(userId, academyId, worksheetId, "rolled-back");
            assertEquals(GenerationStatus.PENDING, accepted.status());
            verify(fixed, never()).transcribe(any(), any());
            transaction.setRollbackOnly();
        });
        assertEquals(0, count("worksheet_generations"));
        assertEquals(0, count("worksheet_transcriptions"));
        verify(fixed, never()).transcribe(any(), any());
    }

    @Test
    void restartRecoveryFailsInterruptedWorkKeepsTranscriptionAndRedispatchesPendingWork() throws Exception {
        AtomicBoolean ready = (AtomicBoolean) ReflectionTestUtils.getField(dispatcher, "ready");
        ready.set(false);
        long interrupted;
        com.ceos24.autedi.domain.generation.persistence.StoredTranscription frozen;
        GenerationWorkStore.Work oldWork;
        long originalWorksheet = worksheetId;
        try {
            interrupted = persistedPending("interrupted");
            oldWork = workStore.claim(interrupted);
            var image = images.getPreparedImage(academyId, worksheetId, 1);
            frozen = workStore.transcribed(oldWork, image, fixed.transcribe(image, GenerationClientMode.FIXED));
            worksheetId = body(mvc.perform(multipart("/api/v1/worksheets")
                                    .file(new MockMultipartFile(
                                            "file", "queued.png", "image/png", TestWorksheetImages.png(2, 3)))
                                    .header(HttpHeaders.AUTHORIZATION, bearer))
                            .andExpect(status().isCreated())
                            .andReturn())
                    .get("id")
                    .longValue();
            long pending = persistedPending("committed-without-enqueue");
            dispatcher.recoverAfterRestart();
            assertEquals("SUCCEEDED", awaitTerminal(pending).get("status").asString());
        } finally {
            ready.set(true);
            worksheetId = originalWorksheet;
        }
        JsonNode interruptedStatus = result(get(base() + "/generations/" + interrupted));
        assertEquals("FAILED", interruptedStatus.get("status").asString());
        assertEquals("AI_CORRECTION", interruptedStatus.get("stage").asString());
        assertEquals("SERVER_INTERRUPTED", interruptedStatus.at("/error/code").asString());
        assertEquals(frozen, archives.restoreTranscription(academyId, frozen.id()));
        // 중단된 이전 워커가 늦게 끝나도 확정 결과를 쓰지 않는다.
        workStore.completeFixed(oldWork, frozen, "late response", null);
        worker.process(interrupted);
        assertEquals(1, count("worksheet_generation_results"));
        dispatcher.recoverAfterRestart();
        assertEquals(1, count("worksheet_generation_results"));
    }

    @Test
    void legacyMockAlsoRunsOutsideTheRequestTransaction() throws Exception {
        legacyUsed = true;
        ReflectionTestUtils.setField(generationService, "fixedResponseEnabled", false);
        AtomicBoolean transaction = new AtomicBoolean(true);
        when(aiClient.generate(any(), anyList(), anyList())).thenAnswer(call -> {
            transaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return new com.ceos24.autedi.domain.generation.client.MockCorrectionAiClient()
                    .generate(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        });
        try {
            assertEquals(
                    "SUCCEEDED", generate("legacy-background").get("status").asString());
            assertFalse(transaction.get());
            assertEquals(3, review().get("corrections").size());
            assertEquals(0, review().get("reviewIssues").size());
            assertEquals(1, count("worksheet_transcriptions"));
            verify(aiClient, times(1)).generate(any(), anyList(), anyList());
        } finally {
            ReflectionTestUtils.setField(generationService, "fixedResponseEnabled", true);
        }
    }

    @Test
    void repeatedCorrectionFailuresReuseTheSameTranscriptionRegardlessOfSettingOrderOrCurrentClientFlag()
            throws Exception {
        ReflectionTestUtils.setField(fixed, "failAt", "AI_CORRECTION");
        JsonNode first = generate("reuse-chain");
        Long transcript = jdbc.queryForObject("select id from worksheet_transcriptions", Long.class);
        var frozen = archives.restoreTranscription(academyId, transcript);
        result(put(base() + "/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areas\":[{\"area\":\"CONTENT_SUMMARY\",\"level\":\"ADVANCED\"},"
                        + "{\"area\":\"GRAMMAR_SPELLING\",\"level\":\"STANDARD\"}]}"));
        ReflectionTestUtils.setField(generationService, "fixedResponseEnabled", false);
        try {
            JsonNode second = awaitTerminal(body(attempt(
                            base() + "/generations/" + first.get("id").longValue() + "/retry", "reuse-chain-2", bearer))
                    .get("id")
                    .longValue());
            assertEquals("FAILED", second.get("status").asString());
            ReflectionTestUtils.setField(fixed, "failAt", "NONE");
            JsonNode third = awaitTerminal(body(attempt(
                            base() + "/generations/" + second.get("id").longValue() + "/retry",
                            "reuse-chain-3",
                            bearer))
                    .get("id")
                    .longValue());
            assertEquals("SUCCEEDED", third.get("status").asString());
            assertEquals(3, third.get("attempt").intValue());
            assertEquals(1, count("worksheet_transcriptions"));
            assertEquals(frozen, archives.restoreTranscription(academyId, transcript));
            var audit = archives.restoreResult(
                    academyId, review().at("/aiResults/0/id").longValue());
            assertEquals(transcript, audit.transcription().id());
            assertEquals(first.get("id").longValue(), audit.transcription().createdByGenerationId());
            assertTrue(review().get("corrections")
                    .valueStream()
                    .allMatch(card -> card.at("/anchor/transcriptionId").longValue() == transcript));
            assertEquals(
                    GenerationClientMode.FIXED,
                    generations
                            .findById(third.get("id").longValue())
                            .orElseThrow()
                            .getClientMode());
            verify(fixed, times(1)).transcribe(any(), any());
            verify(fixed, times(3)).correct(any(), any());
        } finally {
            ReflectionTestUtils.setField(generationService, "fixedResponseEnabled", true);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"text_hash", "source_image_hash"})
    void invalidTranscriptionFallsBackToOcrInsteadOfReusingStaleTextOrCoordinates(String column) throws Exception {
        ReflectionTestUtils.setField(fixed, "failAt", "AI_CORRECTION");
        JsonNode failed = generate("invalid-transcription");
        Long original = jdbc.queryForObject("select id from worksheet_transcriptions", Long.class);
        // 의도적으로 로컬 테스트 DB만 훼손해 무결성 검사를 확인한다.
        jdbc.update("update worksheet_transcriptions set " + column + " = ? where id = ?", "0".repeat(64), original);
        ReflectionTestUtils.setField(fixed, "failAt", "NONE");
        JsonNode accepted = body(attempt(
                base() + "/generations/" + failed.get("id").longValue() + "/retry",
                "invalid-transcription-retry",
                bearer));
        assertEquals(
                "SUCCEEDED",
                awaitTerminal(accepted.get("id").longValue()).get("status").asString());
        assertEquals(2, count("worksheet_transcriptions"));
        Long used = generations
                .findById(accepted.get("id").longValue())
                .orElseThrow()
                .getInputTranscriptionId();
        assertNotEquals(original, used);
        assertEquals(used, review().at("/corrections/0/anchor/transcriptionId").longValue());
        verify(fixed, times(2)).transcribe(any(), any());
    }

    @Test
    void retryRejectsWrongOwnershipNonRetryableChangedFileChangedLevelAndNonLatestFailure() throws Exception {
        ReflectionTestUtils.setField(fixed, "failAt", "AI_CORRECTION");
        long failed = generate("guarded-retry").get("id").longValue();
        String url = base() + "/generations/" + failed + "/retry";
        assertEquals(404, attempt(url, "outsider", otherBearer).getResponse().getStatus());
        long otherWorksheet = upload("another.png");
        assertEquals(
                404,
                attempt(
                                "/api/v1/worksheets/" + otherWorksheet + "/generations/" + failed + "/retry",
                                "wrong-worksheet",
                                bearer)
                        .getResponse()
                        .getStatus());
        User foreignUser = new TransactionTemplate(transactionManager).execute(tx -> user(academy()));
        String mismatchedActor = "Bearer "
                + tokens.createAccessToken(
                        foreignUser.getId(),
                        academyId,
                        foreignUser.getEmail(),
                        foreignUser.getRole().name());
        assertEquals(
                403,
                attempt(url, "wrong-actor-academy", mismatchedActor)
                        .getResponse()
                        .getStatus());
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx -> generations.findById(failed).orElseThrow().fail("TEST", "재시도 금지", false));
        assertEquals(409, attempt(url, "not-retryable", bearer).getResponse().getStatus());
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx -> generations.findById(failed).orElseThrow().fail("TEST", "재시도 허용", true));
        String originalHash = worksheets.findById(worksheetId).orElseThrow().getFileHash();
        jdbc.update("update worksheets set file_hash = ? where id = ?", "f".repeat(64), worksheetId);
        assertEquals(409, attempt(url, "changed-file", bearer).getResponse().getStatus());
        jdbc.update("update worksheets set file_hash = ? where id = ?", originalHash, worksheetId);
        result(put(base() + "/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areas\":[{\"area\":\"GRAMMAR_SPELLING\",\"level\":\"ADVANCED\"},"
                        + "{\"area\":\"CONTENT_SUMMARY\",\"level\":\"ADVANCED\"}]}"));
        assertEquals(409, attempt(url, "changed-level", bearer).getResponse().getStatus());
        configure(true);
        AtomicBoolean ready = (AtomicBoolean) ReflectionTestUtils.getField(dispatcher, "ready");
        ready.set(false);
        try {
            long latest = persistedPending("later-failure");
            // 생성 시간이 같아도 ID가 큰 작업을 최신으로 판단한다.
            jdbc.update(
                    "update worksheet_generations set created_at = (select created_at from worksheet_generations where id = ?) where id = ?",
                    failed,
                    latest);
            workStore.fail(latest, "TEST", "최신 실패", true);
            assertEquals(409, attempt(url, "not-latest", bearer).getResponse().getStatus());
        } finally {
            ready.set(true);
        }
        assertEquals(2, count("worksheet_generations"));
        assertEquals(0, count("worksheet_generation_results"));
        verify(fixed, times(1)).transcribe(any(), any());
        verify(fixed, times(1)).correct(any(), any());
    }

    enum RequestRace {
        CREATE_SAME_KEY,
        RETRY_SAME_KEY,
        RETRY_DIFFERENT_KEYS,
        CREATE_VS_RETRY,
        CREATE_RETRY_SAME_KEY
    }

    @ParameterizedTest
    @EnumSource(RequestRace.class)
    void simultaneousRequestsAcceptAtMostOneNewJobAndReplayOnlyTheSameOperation(RequestRace scenario) throws Exception {
        boolean initial = scenario == RequestRace.CREATE_SAME_KEY;
        long failed = 0;
        if (!initial) {
            ReflectionTestUtils.setField(fixed, "failAt", "AI_CORRECTION");
            failed = generate("before-race").get("id").longValue();
            ReflectionTestUtils.setField(fixed, "failAt", "NONE");
        }
        String create = base() + "/generations", retry = create + "/" + failed + "/retry";
        String firstUrl =
                initial || scenario == RequestRace.CREATE_VS_RETRY || scenario == RequestRace.CREATE_RETRY_SAME_KEY
                        ? create
                        : retry;
        String secondUrl = initial ? create : retry;
        String firstKey = "race-key",
                secondKey =
                        scenario == RequestRace.RETRY_DIFFERENT_KEYS || scenario == RequestRace.CREATE_VS_RETRY
                                ? "other-race-key"
                                : firstKey;
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> {
                    entered.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("race gate timeout");
                    return call.callRealMethod();
                })
                .when(fixed)
                .correct(any(), any());
        long acceptedId;
        try {
            var responses =
                    race(() -> attempt(firstUrl, firstKey, bearer), () -> attempt(secondUrl, secondKey, bearer));
            var successes = responses.stream()
                    .filter(r -> r.getResponse().getStatus() == 202)
                    .toList();
            boolean replay = initial || scenario == RequestRace.RETRY_SAME_KEY;
            assertEquals(replay ? 2 : 1, successes.size());
            assertTrue(responses.stream()
                    .allMatch(r -> List.of(202, 409).contains(r.getResponse().getStatus())));
            acceptedId = body(successes.get(0)).get("id").longValue();
            if (replay)
                assertEquals(acceptedId, body(successes.get(1)).get("id").longValue());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(initial ? 1 : 2, count("worksheet_generations"));
            worker.process(acceptedId);
        } finally {
            release.countDown();
        }
        assertEquals("SUCCEEDED", awaitTerminal(acceptedId).get("status").asString());
        assertEquals(1, count("worksheet_generation_results"));
        assertEquals(4, count("corrections"));
        verify(fixed, times(initial ? 1 : 2)).correct(any(), any());
    }

    @Test
    void simultaneousDifferentWorksheetsCannotClaimTheSameGlobalKeyAndTheLoserGets409() throws Exception {
        long other = upload("same-key.png");
        result(put("/api/v1/worksheets/" + other + "/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areas\":[{\"area\":\"GRAMMAR_SPELLING\",\"level\":\"STANDARD\"}]}"));
        CyclicBarrier bothReadNoKey = new CyclicBarrier(2);
        doAnswer(call -> {
                    bothReadNoKey.await(5, TimeUnit.SECONDS);
                    return java.util.Optional.empty();
                })
                .when(generations)
                .findByIdempotencyKey("global-race");
        var responses = race(
                () -> attempt(base() + "/generations", "global-race", bearer),
                () -> attempt("/api/v1/worksheets/" + other + "/generations", "global-race", bearer));
        assertEquals(
                List.of(202, 409),
                responses.stream()
                        .map(r -> r.getResponse().getStatus())
                        .sorted()
                        .toList());
        assertEquals(1, count("worksheet_generations"));
        int acceptedIndex = responses.get(0).getResponse().getStatus() == 202 ? 0 : 1;
        long original = worksheetId;
        try {
            if (acceptedIndex == 1) worksheetId = other;
            assertEquals(
                    "SUCCEEDED",
                    awaitTerminal(body(responses.get(acceptedIndex)).get("id").longValue())
                            .get("status")
                            .asString());
            assertEquals(1, count("worksheet_generation_results"));
        } finally {
            worksheetId = original;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oldWorkersLateSuccessOrFailureCannotOverwriteASuccessfulRetry(boolean lateFailure) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(call -> {
                    if (calls.incrementAndGet() == 1) {
                        entered.countDown();
                        if (!release.await(5, TimeUnit.SECONDS))
                            throw new IllegalStateException("late result gate timeout");
                        if (lateFailure) throw new GenerationClientFailure("LATE_FAILURE", "늦게 도착한 실패");
                    }
                    return call.callRealMethod();
                })
                .when(fixed)
                .correct(any(), any());
        long old = 0, retried = 0;
        JsonNode successfulReview = null;
        try {
            old = body(attempt(base() + "/generations", "slow-original", bearer))
                    .get("id")
                    .longValue();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            workStore.fail(old, "SERVER_INTERRUPTED", "중단 처리", true);
            retried = body(attempt(base() + "/generations/" + old + "/retry", "retry-while-old-waits", bearer))
                    .get("id")
                    .longValue();
            assertEquals("SUCCEEDED", awaitTerminal(retried).get("status").asString());
            successfulReview = review();
        } finally {
            release.countDown();
            awaitIdle();
        }
        assertEquals(
                "SUCCEEDED",
                result(get(base() + "/generations/" + retried)).get("status").asString());
        assertEquals(
                "SERVER_INTERRUPTED",
                result(get(base() + "/generations/" + old)).at("/error/code").asString());
        assertEquals(successfulReview.get("corrections"), review().get("corrections"));
        assertEquals(1, count("worksheet_generation_results"));
        assertEquals(1, count("worksheet_transcriptions"));
        assertEquals(4, count("corrections"));
        verify(fixed, times(1)).transcribe(any(), any());
        verify(fixed, times(2)).correct(any(), any());
    }

    @Test
    void terraHttpWorkerUsesThePreparedPngCurrentTranscriptAndContractV1ThroughReviewApi() throws Exception {
        try (var upstream = new FakeResponsesServer()) {
            useTerra(upstream);
            upstream.enqueue(200, FakeResponsesServer.transcription(fixedTranscript()));
            String raw = fixedRaw();
            upstream.enqueue(200, FakeResponsesServer.completed(raw));
            assertEquals(
                    "SUCCEEDED", generate("terra-http-success").get("status").asString());
            JsonNode review = review();
            assertEquals(4, review.get("corrections").size());
            assertEquals(3, review.get("reviewIssues").size());
            assertEquals("AI", review.at("/aiResults/0/source").asString());
            assertEquals(
                    AiCorrectionPromptComposer.MODEL,
                    review.at("/aiResults/0/metadata/model").asString());
            assertEquals(
                    AiCorrectionPromptComposer.PROMPT_VERSION,
                    review.at("/aiResults/0/metadata/promptVersion").asString());
            var audit = archives.restoreResult(
                    academyId, review.at("/aiResults/0/id").longValue());
            assertEquals(raw, audit.rawResponse());
            assertEquals(AiCorrectionPromptComposer.MODEL, audit.transcription().model());
            assertEquals(
                    images.getPreparedImage(academyId, worksheetId, 1).sha256(),
                    audit.transcription().sourceImageHash());
            assertEquals(2, upstream.requests().size());
            assertEquals(
                    upstream.requests().get(0).body().at("/input/0/content/1/image_url"),
                    upstream.requests().get(1).body().at("/input/0/content/1/image_url"));
            assertTrue(upstream.requests()
                    .get(1)
                    .body()
                    .at("/input/0/content/0/text")
                    .asString()
                    .contains("I like this book."));
            verify(fixed, never()).transcribe(any(), any());
            verify(fixed, never()).correct(any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terraAuthenticationOrIncompleteCorrectionKeepsTranscriptionWithoutPublishingCards(boolean incomplete)
            throws Exception {
        try (var upstream = new FakeResponsesServer()) {
            useTerra(upstream);
            upstream.enqueue(200, FakeResponsesServer.transcription(fixedTranscript()));
            if (incomplete) {
                var envelope = (tools.jackson.databind.node.ObjectNode)
                        JSON.readTree(FakeResponsesServer.completed(fixedRaw()));
                envelope.put("status", "incomplete");
                envelope.set("incomplete_details", JSON.valueToTree(Map.of("reason", "max_output_tokens")));
                upstream.enqueue(200, envelope.toString());
            } else upstream.enqueue(401, "{\"error\":{\"message\":\"private-upstream-detail\"}}");
            JsonNode failed = generate("terra-http-failure");
            assertEquals("FAILED", failed.get("status").asString());
            assertEquals("AI_CORRECTION", failed.get("stage").asString());
            assertEquals(
                    incomplete ? "TERRA_INCOMPLETE" : "TERRA_AUTHENTICATION",
                    failed.at("/error/code").asString());
            assertEquals(incomplete, failed.at("/error/retryable").asBoolean());
            assertEquals(1, count("worksheet_transcriptions"));
            assertEquals(0, count("worksheet_generation_results"));
            assertEquals(0, count("corrections"));
            assertEquals(
                    AiCorrectionPromptComposer.MODEL,
                    jdbc.queryForObject("select model from worksheet_transcriptions", String.class));
            if (!incomplete)
                assertEquals(
                        409,
                        attempt(
                                        base() + "/generations/"
                                                + failed.get("id").longValue() + "/retry",
                                        "retry-authentication",
                                        bearer)
                                .getResponse()
                                .getStatus());
            assertEquals(2, upstream.requests().size());
            assertFalse(failed.toString().contains("private-upstream-detail"));
        }
    }

    @Test
    void terraTransientFailureIsNotAutomaticallyRetriedAndManualRetryOnlySendsCorrection() throws Exception {
        try (var upstream = new FakeResponsesServer()) {
            useTerra(upstream);
            upstream.enqueue(200, FakeResponsesServer.transcription(fixedTranscript()));
            upstream.enqueue(503, "{\"error\":{\"code\":\"server_error\"}}");
            JsonNode failed = generate("terra-http-transient");
            assertEquals("FAILED", failed.get("status").asString());
            assertTrue(failed.at("/error/retryable").asBoolean());
            assertEquals(2, upstream.requests().size());
            Long transcriptId = jdbc.queryForObject("select id from worksheet_transcriptions", Long.class);
            var frozen = archives.restoreTranscription(academyId, transcriptId);
            upstream.enqueue(200, FakeResponsesServer.completed(fixedRaw()));
            ReflectionTestUtils.setField(generationService, "configuredClient", GenerationClientMode.FIXED);
            JsonNode retry = body(attempt(
                    base() + "/generations/" + failed.get("id").longValue() + "/retry",
                    "terra-http-manual-retry",
                    bearer));
            assertEquals(
                    "SUCCEEDED",
                    awaitTerminal(retry.get("id").longValue()).get("status").asString());
            assertEquals(3, upstream.requests().size());
            assertEquals(1, count("worksheet_transcriptions"));
            assertEquals(frozen, archives.restoreTranscription(academyId, transcriptId));
            assertEquals(
                    "autedi_correction_v1",
                    upstream.requests().get(2).body().at("/text/format/name").asString());
            assertEquals(
                    transcriptId,
                    review().at("/corrections/0/anchor/transcriptionId").longValue());
            assertEquals(
                    GenerationClientMode.TERRA,
                    generations
                            .findById(retry.get("id").longValue())
                            .orElseThrow()
                            .getClientMode());
        }
    }

    @Test
    void fullOfflineFlowPreservesRotatedImageCoordinatesAndAiSourceThroughFailureRetryAndTeacherEdits()
            throws Exception {
        byte[] original = TestWorksheetImages.withOrientation(true, 6, ByteOrder.BIG_ENDIAN);
        JsonNode uploaded = body(mvc.perform(multipart("/api/v1/worksheets")
                        .file(new MockMultipartFile("file", "rotated.jpg", "image/jpeg", original))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andReturn());
        worksheetId = uploaded.get("id").longValue();
        assertEquals("DRAFT", uploaded.get("status").asString());
        assertEquals(3, uploaded.at("/pages/0/width").intValue());
        assertEquals(2, uploaded.at("/pages/0/height").intValue());
        mvc.perform(get(base() + "/file").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(content().bytes(original));
        byte[] visiblePng = mvc.perform(get(base() + "/pages/1/image").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertEquals(3, ImageIO.read(new ByteArrayInputStream(visiblePng)).getWidth());
        assertEquals(2, ImageIO.read(new ByteArrayInputStream(visiblePng)).getHeight());
        configure(true);
        CountDownLatch ocrEntered = new CountDownLatch(1), releaseOcr = new CountDownLatch(1);
        CountDownLatch correctionEntered = new CountDownLatch(1), releaseCorrection = new CountDownLatch(1);
        AtomicReference<Object> ocrImage = new AtomicReference<>(), correctionImage = new AtomicReference<>();
        doAnswer(call -> {
                    ocrImage.set(call.getArgument(0));
                    ocrEntered.countDown();
                    if (!releaseOcr.await(5, TimeUnit.SECONDS))
                        throw new IllegalStateException("end-to-end OCR timeout");
                    return call.callRealMethod();
                })
                .when(fixed)
                .transcribe(any(), any());
        AtomicInteger correctionCalls = new AtomicInteger();
        doAnswer(call -> {
                    correctionImage.set(call.getArgument(0));
                    if (correctionCalls.incrementAndGet() == 1) {
                        correctionEntered.countDown();
                        if (!releaseCorrection.await(5, TimeUnit.SECONDS))
                            throw new IllegalStateException("end-to-end correction timeout");
                    }
                    return call.callRealMethod();
                })
                .when(fixed)
                .correct(any(), any());
        ReflectionTestUtils.setField(fixed, "failAt", "AI_CORRECTION");
        long firstId;
        StoredTranscription frozen;
        try {
            MvcResult accepted = attempt(base() + "/generations", "offline-complete-flow", bearer);
            assertEquals(202, accepted.getResponse().getStatus());
            JsonNode pending = body(accepted);
            firstId = pending.get("id").longValue();
            assertEquals("PENDING", pending.get("status").asString());
            assertTrue(ocrEntered.await(5, TimeUnit.SECONDS));
            assertEquals(
                    "OCR",
                    result(get(base() + "/generations/" + firstId)).get("stage").asString());
            assertEquals(0, count("worksheet_transcriptions"));
            releaseOcr.countDown();
            assertTrue(correctionEntered.await(5, TimeUnit.SECONDS));
            assertSame(ocrImage.get(), correctionImage.get());
            assertEquals(
                    "AI_CORRECTION",
                    result(get(base() + "/generations/" + firstId)).get("stage").asString());
            assertEquals(1, count("worksheet_transcriptions"));
            assertEquals(0, count("corrections"));
            Long transcriptionId = jdbc.queryForObject("select id from worksheet_transcriptions", Long.class);
            frozen = archives.restoreTranscription(academyId, transcriptionId);
            assertEquals(LocalStorageService.sha256(original), frozen.sourceFileHash());
            assertEquals(LocalStorageService.sha256(visiblePng), frozen.sourceImageHash());
            assertEquals(3, frozen.imageWidth());
            assertEquals(2, frozen.imageHeight());
        } finally {
            releaseOcr.countDown();
            releaseCorrection.countDown();
        }
        JsonNode failed = awaitTerminal(firstId);
        assertEquals("FAILED", failed.get("status").asString());
        assertEquals("AI_CORRECTION_FAILED", failed.at("/error/code").asString());
        assertEquals("FAILED", result(get(base())).get("status").asString());
        assertEquals(frozen, archives.restoreTranscription(academyId, frozen.id()));
        assertEquals(0, count("worksheet_generation_results"));
        mvc.perform(get(base() + "/review").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict());
        ReflectionTestUtils.setField(fixed, "failAt", "NONE");
        MvcResult acceptedRetry =
                attempt(base() + "/generations/" + firstId + "/retry", "offline-complete-retry", bearer);
        assertEquals(202, acceptedRetry.getResponse().getStatus());
        long retryId = body(acceptedRetry).get("id").longValue();
        JsonNode completed = awaitTerminal(retryId);
        assertEquals("SUCCEEDED", completed.get("status").asString());
        assertEquals("COMPLETED", completed.get("stage").asString());
        assertEquals(2, completed.get("attempt").intValue());
        assertEquals(1, count("worksheet_transcriptions"));
        assertEquals(1, count("worksheet_generation_results"));
        assertEquals(4, count("corrections"));
        JsonNode beforeEdit = review();
        assertEquals("READY", beforeEdit.at("/worksheet/status").asString());
        assertEquals(3, beforeEdit.at("/worksheet/pages/0/width").intValue());
        assertEquals(2, beforeEdit.at("/worksheet/pages/0/height").intValue());
        assertEquals(3, beforeEdit.get("reviewIssues").size());
        assertEquals(1, omitted(beforeEdit));
        var audit = archives.restoreResult(
                academyId, beforeEdit.at("/aiResults/0/id").longValue());
        assertEquals(frozen, audit.transcription());
        JsonNode replace = beforeEdit
                .get("corrections")
                .valueStream()
                .filter(card -> card.get("action").asString().equals("REPLACE")
                        && card.at("/anchor/occurrence").intValue() == 2)
                .findFirst()
                .orElseThrow();
        assertEquals(
                frozen.id().longValue(), replace.at("/anchor/transcriptionId").longValue());
        assertEquals(11, replace.at("/anchor/start").intValue());
        assertEquals(13, replace.at("/anchor/end").intValue());
        assertEquals("go", frozen.lines().getFirst().substring(11, 13));
        assertEquals("NORMALIZED_0_1", replace.get("regionCoordinateUnit").asString());
        JsonNode region = replace.at("/regions/0");
        assertEquals(
                uploaded.at("/pages/0/id").longValue(), region.get("pageId").longValue());
        // 모형의 LTRB [0.4,0.1,0.5,0.2]가 API의 XYWH [0.4,0.1,0.1,0.1]로 바뀐다.
        assertEquals(0.4, region.get("x").doubleValue(), 1e-12);
        assertEquals(0.1, region.get("y").doubleValue(), 1e-12);
        assertEquals(0.1, region.get("width").doubleValue(), 1e-12);
        assertEquals(0.1, region.get("height").doubleValue(), 1e-12);
        // 픽셀 환산은 원본 2x3이 아니라 화면에 보이는 보정 PNG 3x2를 사용하며 반올림하지 않는다.
        assertEquals(1.2, region.get("x").doubleValue() * frozen.imageWidth(), 1e-12);
        assertEquals(0.2, region.get("y").doubleValue() * frozen.imageHeight(), 1e-12);
        assertEquals(0.3, region.get("width").doubleValue() * frozen.imageWidth(), 1e-12);
        assertEquals(0.2, region.get("height").doubleValue() * frozen.imageHeight(), 1e-12);
        JsonNode invalidBoxCard = beforeEdit
                .get("corrections")
                .valueStream()
                .filter(card -> hasWarning(card, "INVALID_BOX"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, invalidBoxCard.get("regions").size());
        assertTrue(invalidBoxCard.get("requiresReview").asBoolean());
        for (JsonNode initial : List.of(replace, card(beforeEdit, AiAction.GUIDANCE))) {
            String url = base() + "/corrections/" + initial.get("id").longValue();
            String edited = initial.get("action").asString().equals("REPLACE")
                    ? "teacher replacement"
                    : "Add a reason and an example.";
            JsonNode updated = result(patch(url)
                    .header(HttpHeaders.IF_MATCH, initial.get("etag").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(Map.of("content", edited))));
            assertEquals(edited, updated.get("content").asString());
            assertTrue(updated.get("editedByTeacher").asBoolean());
            JsonNode stored = result(get(url));
            assertEquals(edited, stored.get("content").asString());
            assertPreserved(initial, stored);
        }
        assertEquals(audit, archives.restoreResult(academyId, audit.id()));
        assertEquals(beforeEdit.get("reviewIssues"), review().get("reviewIssues"));
        assertArrayEquals(
                visiblePng, images.getPreparedImage(academyId, worksheetId, 1).png());
        mvc.perform(get(base() + "/file").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(content().bytes(original));
        verify(fixed, times(1)).transcribe(any(), any());
        verify(fixed, times(2)).correct(any(), any());
        verifyNoInteractions(terra, aiClient);
    }

    private void useTerra(FakeResponsesServer upstream) {
        terraProperties.setEndpoint(upstream.endpoint());
        ReflectionTestUtils.setField(generationService, "configuredClient", GenerationClientMode.TERRA);
    }

    private static String fixedTranscript() throws Exception {
        try (var input = FixedResponseReviewApiTest.class.getResourceAsStream("/ai/fixed/v1/transcription.txt")) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static String fixedRaw() throws Exception {
        try (var input = FixedResponseReviewApiTest.class.getResourceAsStream("/ai/fixed/v1/response.json")) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private MvcResult attempt(String url, String key, String authorization) throws Exception {
        return mvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .header("Idempotency-Key", key))
                .andReturn();
    }

    private long upload(String name) throws Exception {
        return body(mvc.perform(multipart("/api/v1/worksheets")
                                .file(new MockMultipartFile("file", name, "image/png", TestWorksheetImages.png(2, 3)))
                                .header(HttpHeaders.AUTHORIZATION, bearer))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .longValue();
    }

    private static List<MvcResult> race(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<MvcResult> a = callers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return first.call();
            });
            Future<MvcResult> b = callers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return second.call();
            });
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally {
            callers.shutdownNow();
        }
    }

    private void awaitIdle() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (executor.getActiveCount() > 0
                || !executor.getThreadPoolExecutor().getQueue().isEmpty()) {
            if (System.nanoTime() > deadline) fail("백그라운드 작업이 종료되지 않았습니다.");
            Thread.sleep(20);
        }
    }

    private long persistedPending(String key) {
        return new TransactionTemplate(transactionManager).execute(transaction -> {
            var worksheet = worksheets.findById(worksheetId).orElseThrow();
            worksheet.updateStatus(WorksheetStatus.PROCESSING);
            return generations
                    .save(WorksheetGeneration.builder()
                            .worksheet(worksheet)
                            .requestedBy(users.findById(userId).orElseThrow())
                            .idempotencyKey(key)
                            .requestFileHash(worksheet.getFileHash())
                            .clientMode(GenerationClientMode.FIXED)
                            .requestSettingsSnapshot(List.of(
                                    Map.of("area", "GRAMMAR_SPELLING", "level", "STANDARD"),
                                    Map.of("area", "CONTENT_SUMMARY", "level", "ADVANCED")))
                            .build())
                    .getId();
        });
    }

    private void configure(boolean content) throws Exception {
        var areas = content
                ? List.of(
                        Map.of("area", "GRAMMAR_SPELLING", "level", "STANDARD"),
                        Map.of("area", "CONTENT_SUMMARY", "level", "ADVANCED"))
                : List.of(Map.of("area", "GRAMMAR_SPELLING", "level", "STANDARD"));
        result(put(base() + "/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("areas", areas))));
    }

    private JsonNode generate(String key) throws Exception {
        JsonNode accepted = body(mvc.perform(post(base() + "/generations")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header("Idempotency-Key", key))
                .andExpect(status().isAccepted())
                .andReturn());
        return awaitTerminal(accepted.get("id").longValue());
    }

    private JsonNode awaitTerminal(long id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JsonNode status;
        do {
            status = result(get(base() + "/generations/" + id));
            if (List.of("SUCCEEDED", "FAILED").contains(status.get("status").asString())) return status;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        fail("작업이 종료되지 않았습니다: " + status);
        return status;
    }

    private JsonNode review() throws Exception {
        return result(get(base() + "/review"));
    }

    private String base() {
        return "/api/v1/worksheets/" + worksheetId;
    }

    private JsonNode result(MockHttpServletRequestBuilder request) throws Exception {
        return body(mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static JsonNode body(MvcResult result) {
        return JSON.readTree(result.getResponse().getContentAsByteArray()).get("result");
    }

    private static JsonNode card(JsonNode review, AiAction action) {
        return review.get("corrections")
                .valueStream()
                .filter(item -> item.get("action").asString().equals(action.name()))
                .findFirst()
                .orElseThrow();
    }

    private static int omitted(JsonNode review) {
        return review.get("summary")
                .valueStream()
                .mapToInt(area -> area.get("omittedCount").intValue())
                .sum();
    }

    private static boolean hasWarning(JsonNode item, String code) {
        return item.get("warnings")
                .valueStream()
                .anyMatch(warning -> warning.get("code").asString().equals(code));
    }

    private static void assertPreserved(JsonNode before, JsonNode after) {
        for (String field : List.of(
                "action",
                "feedbackType",
                "originalText",
                "aiInitialContent",
                "explanation",
                "anchor",
                "regions",
                "locationUncertain",
                "warnings",
                "requiresReview")) assertEquals(before.get(field), after.get(field), field);
    }

    private Academy academy() {
        return academies.save(Academy.builder()
                .name("고정 응답 테스트")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusDays(10))
                .build());
    }

    private User user(Academy academy) {
        return users.save(User.builder()
                .academy(academy)
                .email(UUID.randomUUID() + "@fixed-test.invalid")
                .passwordHash("unused-test-value")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .name("교사")
                .build());
    }

    private String bearer(User user) {
        return "Bearer "
                + tokens.createAccessToken(
                        user.getId(),
                        user.getAcademy().getId(),
                        user.getEmail(),
                        user.getRole().name());
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private void localDatabase() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:fixed_review_api"));
        }
    }
}
