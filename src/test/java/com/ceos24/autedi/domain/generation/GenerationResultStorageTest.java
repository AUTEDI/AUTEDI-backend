package com.ceos24.autedi.domain.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.generation.client.GenerationClientProfiles;
import com.ceos24.autedi.domain.generation.contract.GenerationAiProfile;
import com.ceos24.autedi.domain.generation.contract.GenerationContractRouter;
import com.ceos24.autedi.domain.generation.contract.GenerationContractVersion;
import com.ceos24.autedi.domain.generation.contract.v1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.AiCorrectionResponseParser;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionResponseValidator;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationCode;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationContext;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiValidationResult;
import com.ceos24.autedi.domain.generation.contract.v1_1.prompt.AiCorrectionPromptVariant;
import com.ceos24.autedi.domain.generation.entity.GenerationClientMode;
import com.ceos24.autedi.domain.generation.entity.GenerationResultStatus;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.persistence.GenerationResultMetadata;
import com.ceos24.autedi.domain.generation.persistence.StoredGenerationResult;
import com.ceos24.autedi.domain.generation.persistence.StoredTranscription;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.generation.service.GenerationResultStorageService;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.service.PreparedWorksheetImage;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.storage.FileStorageService;
import com.ceos24.autedi.global.storage.LocalStorageService;
import com.ceos24.autedi.support.TestWorksheetImages;
import jakarta.persistence.EntityManager;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 메모리 H2에 실제 커밋하고 다른 영속성 컨텍스트에서 아카이브를 복원한다. 외부 API는 사용하지 않는다. */
@ActiveProfiles("test")
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:autedi_archive_storage;DB_CLOSE_DELAY=-1;MODE=MySQL",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class GenerationResultStorageTest {
    @TempDir
    static Path uploadRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.upload-dir", () -> uploadRoot.toString());
        registry.add("app.backend-url", () -> "http://archive-test.invalid");
    }

    private static final GenerationResultMetadata META = GenerationResultMetadata.current();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> LINES = List.of("😀 go then go", "", "I like this book.", "");
    private static final Map<AiArea, AiCorrectionLevel> SETTINGS = Map.of(
            AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD,
            AiArea.CONTENT_SUMMARY, AiCorrectionLevel.ADVANCED);

    @Autowired
    GenerationResultStorageService storage;

    @Autowired
    GenerationContractRouter contracts;

    @Autowired
    WorksheetService worksheets;

    @Autowired
    WorksheetRepository worksheetRepository;

    @Autowired
    WorksheetPageRepository pages;

    @Autowired
    WorksheetGenerationRepository generations;

    @Autowired
    CorrectionRepository corrections;

    @Autowired
    AcademyRepository academies;

    @Autowired
    UserRepository users;

    @Autowired
    FileStorageService files;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    private TransactionTemplate tx;
    private Long academyId, otherAcademyId, userId, worksheetId, generationId;
    private PreparedWorksheetImage image;
    private String raw;
    private AiValidationResult validation;

    @BeforeEach
    void setup() throws Exception {
        assertLocalH2();
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Academy academy = academies.save(Academy.builder()
                    .name("저장 테스트 학원")
                    .subscriptionStatus(SubscriptionStatus.ACTIVE)
                    .planExpiresAt(LocalDateTime.now().plusDays(10))
                    .build());
            academyId = academy.getId();
            otherAcademyId = academies
                    .save(Academy.builder()
                            .name("다른 학원")
                            .subscriptionStatus(SubscriptionStatus.ACTIVE)
                            .planExpiresAt(LocalDateTime.now().plusDays(10))
                            .build())
                    .getId();
            userId = users.save(User.builder()
                            .academy(academy)
                            .name("저장 테스트")
                            .email(UUID.randomUUID() + "@archive-test.invalid")
                            .passwordHash("unused-test-value")
                            .role(Role.TEACHER)
                            .status(UserStatus.ACTIVE)
                            .build())
                    .getId();
        });
        worksheetId = worksheets
                .uploadWorksheet(
                        userId,
                        academyId,
                        new MockMultipartFile(
                                "file",
                                "original.jpg",
                                "image/jpeg",
                                TestWorksheetImages.withOrientation(true, 6, ByteOrder.LITTLE_ENDIAN)))
                .id();
        generationId = newGeneration(worksheetId, SETTINGS);
        image = worksheets.getPreparedImage(academyId, worksheetId, 1);
        // 64KB를 넘는 원문과 한국어·이모지·여백까지 원래 문자열 그대로 복원하는지 검사한다.
        raw = "\n" + " ".repeat(70_000) + resource("/ai/contract/v1/postprocessing-candidates.json") + "\n  ";
        validation = validate(raw, SETTINGS);
    }

    @AfterEach
    void cleanupOnlyThisMemoryDatabase() throws Exception {
        assertLocalH2();
        for (String table : List.of(
                "worksheet_generation_results",
                "corrections",
                "worksheet_transcriptions",
                "worksheet_generations",
                "worksheet_settings",
                "worksheet_pages",
                "worksheets",
                "users",
                "academies")) {
            jdbc.update("delete from " + table);
        }
    }

    @Test
    void commitsAndRestoresAllCandidatesRawBodyProvenanceAndV3Cards() {
        Long transcriptionId = transcript();
        Long resultId = storage.recordValidatedResult(academyId, generationId, transcriptionId, META, raw, validation);
        StoredGenerationResult restored = storage.restoreResult(academyId, resultId);
        assertEquals(GenerationResultStatus.VALIDATED, restored.status());
        assertEquals(raw, restored.rawResponse());
        assertEquals(META, restored.metadata());
        assertEquals(validation, restored.validationResult());
        assertEquals(LINES, restored.transcription().lines());
        assertEquals(4, restored.correctionLinks().size());
        assertEquals(4, restored.validationResult().corrections().size());
        assertEquals(3, restored.validationResult().reviewIssues().size());
        assertEquals(1, restored.validationResult().excludedItems().size());
        assertEquals(
                List.of(0, 1, 6, 7),
                restored.correctionLinks().stream()
                        .map(link -> link.candidateIndex())
                        .toList());
        assertEquals(4, count("corrections"));
        Correction replace = card(restored, 0);
        assertEquals(AiAction.REPLACE, replace.getAction());
        assertEquals(transcriptionId, replace.getAnchor().transcriptionId());
        assertEquals(11, replace.getAnchor().start());
        assertEquals(13, replace.getAnchor().end());
        assertEquals(2, replace.getAnchor().occurrence());
        assertEquals("went", replace.getAiInitialContent());
        assertFalse(replace.getRequiresReview());
        Correction guidance = card(restored, 1);
        assertEquals(AiAction.GUIDANCE, guidance.getAction());
        assertEquals(CorrectionArea.CONTENT_SUMMARY, guidance.getArea());
        assertEquals("Add one reason you like this book.", guidance.getContent());
        assertEquals("", card(restored, 6).getContent());
        assertFalse(card(restored, 6).getLocationUncertain());
        assertTrue(card(restored, 6).getRequiresReview());
        assertTrue(card(restored, 6).getWarnings().stream()
                .anyMatch(issue -> issue.code() == AiValidationCode.NO_VALID_BOXES));
        Correction partialBox = card(restored, 7);
        assertEquals(1, partialBox.getRegions().size());
        assertEquals(image.pageId(), partialBox.getRegions().getFirst().pageId());
        assertEquals(0.1, partialBox.getRegions().getFirst().x(), 1e-12);
        assertEquals(0.1, partialBox.getRegions().getFirst().width(), 1e-12);
        assertEquals(
                2,
                restored.validationResult()
                        .candidates()
                        .get(7)
                        .original()
                        .boxes()
                        .size());
        assertEquals(image.sha256(), restored.transcription().sourceImageHash());
        assertEquals(3, restored.transcription().imageWidth());
        assertEquals(2, restored.transcription().imageHeight());
        assertTrue(restored.transcription().sourceImageUrl().endsWith("-prepared.png"));
        assertNotEquals(image.imageUrl(), restored.transcription().sourceImageUrl());
    }

    @Test
    void teacherEditDeleteAndRestoreKeepInitialContentAndAuditImmutable() {
        Long resultId = storage.recordValidatedResult(academyId, generationId, transcript(), META, raw, validation);
        StoredGenerationResult initial = storage.restoreResult(academyId, resultId);
        Long correctionId = initial.correctionLinks().getFirst().correctionId();
        tx.executeWithoutResult(status -> {
            Correction correction = corrections.findById(correctionId).orElseThrow();
            correction.editContent("teacher edit");
            correction.delete();
        });
        Correction deleted = loadCard(correctionId);
        assertEquals(CorrectionStatus.DELETED, deleted.getStatus());
        assertEquals("teacher edit", deleted.getContent());
        assertEquals("went", deleted.getAiInitialContent());
        assertTrue(deleted.getIsEdited());
        assertEquals(initial, storage.restoreResult(academyId, resultId));
        tx.executeWithoutResult(
                status -> corrections.findById(correctionId).orElseThrow().restore());
        assertEquals(CorrectionStatus.ACTIVE, loadCard(correctionId).getStatus());
        assertEquals("teacher edit", loadCard(correctionId).getContent());
        assertEquals(initial, storage.restoreResult(academyId, resultId));
        Long guidanceId = initial.correctionLinks().stream()
                .filter(link -> link.candidateIndex() == 1)
                .findFirst()
                .orElseThrow()
                .correctionId();
        assertThrows(
                IllegalArgumentException.class,
                () -> tx.executeWithoutResult(
                        status -> corrections.findById(guidanceId).orElseThrow().editContent(" ")));
    }

    @Test
    void fileReplacementAndOcrCacheMutationCannotChangeOldTranscriptionOrResult() throws Exception {
        Long transcriptionId = transcript();
        Long resultId = storage.recordValidatedResult(academyId, generationId, transcriptionId, META, raw, validation);
        StoredGenerationResult before = storage.restoreResult(academyId, resultId);
        tx.executeWithoutResult(status -> pages.findById(image.pageId())
                .orElseThrow()
                .updateOcrResult(Map.of("text", "different cache"), "cache"));
        worksheets.replaceWorksheetFile(
                academyId,
                worksheetId,
                null,
                new MockMultipartFile("file", "next.png", "image/png", TestWorksheetImages.png(4, 2)));
        assertEquals(before, storage.restoreResult(academyId, resultId));
        StoredTranscription saved = storage.restoreTranscription(academyId, transcriptionId);
        assertEquals(LINES, saved.lines());
        assertEquals(3, saved.imageWidth());
        assertEquals(2, saved.imageHeight());
        assertEquals(
                image.sha256(),
                LocalStorageService.sha256(files.load(saved.sourceImageUrl()).getContentAsByteArray()));
        PreparedWorksheetImage next = worksheets.getPreparedImage(academyId, worksheetId, 1);
        Long nextGeneration = newGeneration(worksheetId, SETTINGS);
        Long nextTranscription =
                storage.recordTranscription(academyId, nextGeneration, next, List.of("new text"), META.model());
        assertNotEquals(transcriptionId, nextTranscription);
        assertEquals(
                LINES, storage.restoreTranscription(academyId, transcriptionId).lines());
        assertEquals(
                List.of("new text"),
                storage.restoreTranscription(academyId, nextTranscription).lines());
    }

    @Test
    void unreadableResponseIsPreservedWithoutCardsAndWithoutLosingCommittedTranscription() {
        Long transcriptionId = transcript();
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.recordValidatedResult(
                        academyId, generationId, transcriptionId, META, "not-json", validation));
        assertEquals(1, count("worksheet_transcriptions"));
        assertEquals(0, count("worksheet_generation_results"));
        String unreadable = "\nnot-json\n확인 필요\n";
        Long resultId = storage.recordUnreadableResult(
                academyId, generationId, transcriptionId, META, unreadable, "JSON 구조 해석 실패");
        StoredGenerationResult result = storage.restoreResult(academyId, resultId);
        assertEquals(GenerationResultStatus.UNREADABLE, result.status());
        assertEquals(unreadable, result.rawResponse());
        assertEquals("JSON 구조 해석 실패", result.parseError());
        assertNull(result.validationResult());
        assertTrue(result.correctionLinks().isEmpty());
        assertEquals(0, count("corrections"));
        assertEquals(transcriptionId, result.transcription().id());
        assertThrows(
                IllegalStateException.class,
                () -> storage.recordValidatedResult(academyId, generationId, transcriptionId, META, raw, validation));
        assertEquals(unreadable, storage.restoreResult(academyId, resultId).rawResponse());
    }

    @Test
    void emptyItemsRemainSuccessfulAndDoNotCreateArtificialCards() {
        String empty = "{\"items\":[]}";
        Long id = storage.recordValidatedResult(
                academyId, generationId, transcript(), META, empty, validate(empty, SETTINGS));
        StoredGenerationResult restored = storage.restoreResult(academyId, id);
        assertEquals(GenerationResultStatus.VALIDATED, restored.status());
        assertEquals(0, count("corrections"));
        assertTrue(restored.validationResult().candidates().isEmpty());
        assertNull(restored.parseError());
    }

    @Test
    void exactRepeatsAreIdempotentAndChangedResponsesOrTextCannotOverwriteHistory() {
        Long transcriptionId = transcript();
        assertEquals(transcriptionId, transcript());
        Long resultId = storage.recordValidatedResult(academyId, generationId, transcriptionId, META, raw, validation);
        assertEquals(
                resultId,
                storage.recordValidatedResult(academyId, generationId, transcriptionId, META, raw, validation));
        assertEquals(4, count("corrections"));
        assertThrows(
                IllegalStateException.class,
                () -> storage.recordTranscription(academyId, generationId, image, List.of("changed"), META.model()));
        assertThrows(
                IllegalStateException.class,
                () -> storage.recordValidatedResult(
                        academyId, generationId, transcriptionId, META, raw + " ", validation));
        assertEquals(raw, storage.restoreResult(academyId, resultId).rawResponse());
        assertEquals(1, count("worksheet_generation_results"));
    }

    @Test
    void resultAndCardsRollBackTogetherButEarlierTranscriptionRemains() {
        Long transcriptionId = transcript();
        tx.executeWithoutResult(status -> {
            storage.recordValidatedResult(academyId, generationId, transcriptionId, META, raw, validation);
            entityManager.flush();
            assertEquals(4, count("corrections"));
            assertEquals(1, count("worksheet_generation_results"));
            status.setRollbackOnly();
        });
        assertEquals(0, count("corrections"));
        assertEquals(0, count("worksheet_generation_results"));
        assertEquals(
                LINES, storage.restoreTranscription(academyId, transcriptionId).lines());
    }

    @Test
    void tenantAndBindingGuardsPreventCrossResourceWritesAndForgedValidation() throws Exception {
        Long transcriptionId = transcript();
        Long resultId = storage.recordValidatedResult(academyId, generationId, transcriptionId, META, raw, validation);
        assertEquals(
                ErrorCode.TRANSCRIPTION_NOT_FOUND,
                assertThrows(CustomException.class, () -> storage.restoreTranscription(otherAcademyId, transcriptionId))
                        .getErrorCode());
        assertEquals(
                ErrorCode.GENERATION_RESULT_NOT_FOUND,
                assertThrows(CustomException.class, () -> storage.restoreResult(otherAcademyId, resultId))
                        .getErrorCode());
        assertEquals(
                ErrorCode.GENERATION_NOT_FOUND,
                assertThrows(
                                CustomException.class,
                                () -> storage.recordValidatedResult(
                                        otherAcademyId, generationId, transcriptionId, META, raw, validation))
                        .getErrorCode());
        Long nextGeneration = newGeneration(worksheetId, SETTINGS);
        Map<AiArea, AiCorrectionLevel> forged = Map.of(
                AiArea.GRAMMAR_SPELLING,
                AiCorrectionLevel.ADVANCED,
                AiArea.CONTENT_SUMMARY,
                AiCorrectionLevel.ADVANCED);
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.recordValidatedResult(
                        academyId, nextGeneration, transcriptionId, META, raw, validate(raw, forged)));
        byte[] changed = image.png();
        changed[0] = 0;
        PreparedWorksheetImage wrongImage = new PreparedWorksheetImage(
                image.worksheetId(),
                image.pageId(),
                1,
                image.imageUrl(),
                image.width(),
                image.height(),
                image.sha256(),
                changed);
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.recordTranscription(academyId, nextGeneration, wrongImage, LINES, META.model()));
        Long otherWorksheet = worksheets
                .uploadWorksheet(
                        userId,
                        academyId,
                        new MockMultipartFile("file", "other.png", "image/png", TestWorksheetImages.png(2, 3)))
                .id();
        Long otherGeneration = newGeneration(otherWorksheet, SETTINGS);
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.recordValidatedResult(
                        academyId, otherGeneration, transcriptionId, META, raw, validation));
        assertEquals(1, count("worksheet_generation_results"));
        assertEquals(4, count("corrections"));
    }

    @Test
    void retryCanReferenceTheSameImmutableTranscriptionWithItsOwnSettingsAndVersionMetadata() {
        Long transcriptionId = transcript();
        Long first = storage.recordValidatedResult(academyId, generationId, transcriptionId, META, raw, validation);
        Map<AiArea, AiCorrectionLevel> changedSettings = Map.of(
                AiArea.GRAMMAR_SPELLING,
                AiCorrectionLevel.ADVANCED,
                AiArea.CONTENT_SUMMARY,
                AiCorrectionLevel.STANDARD);
        Long retry = newGeneration(worksheetId, changedSettings);
        GenerationResultMetadata retryMetadata = new GenerationResultMetadata(
                META.model(), META.contractVersion(), "offline-prompt-revision-2", META.validationVersion());
        Long second = storage.recordValidatedResult(
                academyId, retry, transcriptionId, retryMetadata, raw, validate(raw, changedSettings));
        assertNotEquals(first, second);
        assertEquals(
                transcriptionId,
                storage.restoreResult(academyId, first).transcription().id());
        assertEquals(
                transcriptionId,
                storage.restoreResult(academyId, second).transcription().id());
        assertEquals(
                generationId,
                storage.restoreResult(academyId, second).transcription().createdByGenerationId());
        assertEquals(retry, storage.restoreResult(academyId, second).generationId());
        assertEquals(META, storage.restoreResult(academyId, first).metadata());
        assertEquals(retryMetadata, storage.restoreResult(academyId, second).metadata());
        assertEquals(1, count("worksheet_transcriptions"));
        assertEquals(2, count("worksheet_generation_results"));
    }

    @Test
    void legacyRowsStayUnclassifiedAndDatabaseRejectsPartialOrReviewCorrectionFields() {
        Long id = tx.execute(status -> corrections
                .save(Correction.builder()
                        .worksheet(worksheetRepository.findById(worksheetId).orElseThrow())
                        .page(pages.findById(image.pageId()).orElseThrow())
                        .generation(generations.findById(generationId).orElseThrow())
                        .area(CorrectionArea.GRAMMAR_SPELLING)
                        .originalText("go")
                        .aiInitialContent("went")
                        .explanation("기존 Mock 데이터")
                        .regions(List.of())
                        .build())
                .getId());
        assertNull(loadCard(id).getAction());
        assertNull(loadCard(id).getAnchor());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("update corrections set action='REPLACE' where id=?", id));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("update corrections set action='REVIEW' where id=?", id));
        assertNull(loadCard(id).getAction());
        assertNull(loadCard(id).getAnchor());
    }

    @ParameterizedTest(name = "v1.1 저장·복원: {0}")
    @MethodSource("acceptedV11Cases")
    void commitsAndRestoresEveryAcceptedV11FixtureWithoutLosingCandidatesOrEdits(String fixtureId) throws Exception {
        var input = v11Case(fixtureId);
        long id = saveV11(input);
        var restored = storage.restoreResult(academyId, id);
        assertEquals(GenerationResultStatus.VALIDATED, restored.status());
        assertEquals(input.raw(), restored.rawResponse());
        assertEquals(input.profile().metadata(), restored.metadata());
        assertEquals(input.validation(), restored.validationResultV11());
        assertInstanceOf(GenerationContractRouter.V11Validation.class, restored.versionedValidationResult());
        assertThrows(IllegalStateException.class, restored::validationResult);
        assertEquals(
                input.validation().cards().size(), restored.correctionLinks().size());
        String storedJson = jdbc.queryForObject(
                "select validation_result from worksheet_generation_results where id=?", String.class, id);
        assertTrue(JSON.readTree(storedJson).isObject(), "JSON 문자열의 이중 인코딩 금지");
        assertTrue(JSON.readTree(storedJson).has("originalResponse"), "기존 JSON 루트 형태 유지");
        for (var link : restored.correctionLinks()) {
            var candidate = input.validation().candidates().get(link.candidateIndex());
            var card = loadCard(link.correctionId());
            var details = card.getV11Details();
            assertNotNull(details);
            assertEquals(candidate.location().originalText(), card.getOriginalText());
            assertEquals(
                    candidate.original().anchors().size(), details.anchors().size());
            assertEquals(candidate.original().revisionScope(), details.revisionScope());
            assertEquals(candidate.original().edit(), details.aiInitialEdit());
            assertEquals(candidate.original().edit(), details.edit());
            assertEquals(candidate.issues(), details.warnings());
            assertEquals(candidate.requiresReview(), card.getRequiresReview());
            assertEquals(
                    candidate.location().validTargetBoxes().size(),
                    card.getRegions().size());
            for (var anchor : details.anchors()) assertEquals(input.transcriptionId(), anchor.transcriptionId());
            if (details.anchors().size() == 1) assertEquals(details.anchors().getFirst(), card.getAnchor());
            else assertNull(card.getAnchor(), "다중 줄을 단일 anchor로 축약하지 않는다.");
        }
        assertEquals(
                input.validation().reviewIssues(),
                restored.validationResultV11().reviewIssues());
        assertEquals(
                input.validation().excludedItems(),
                restored.validationResultV11().excludedItems());
    }

    @Test
    void v1AndV11ArchivesCoexistAndOldV1JsonIsStillReadable() throws Exception {
        long oldId = storage.recordValidatedResult(academyId, generationId, transcript(), META, raw, validation);
        var next = v11Case("advanced-structure-multiline");
        long newId = saveV11(next);
        assertEquals(validation, storage.restoreResult(academyId, oldId).validationResult());
        assertNull(storage.restoreResult(academyId, oldId).validationResultV11());
        assertEquals(next.validation(), storage.restoreResult(academyId, newId).validationResultV11());
        assertEquals(raw, storage.restoreResult(academyId, oldId).rawResponse());
        assertEquals(2, count("worksheet_generation_results"));
        // 새 래퍼·버전 키 없이 이전 v1 직렬화 JSON을 직접 넣어도 같은 객체로 복원한다.
        jdbc.update(
                "update worksheet_generation_results set validation_result=? format json where id=?",
                JSON.writeValueAsString(validation),
                oldId);
        assertEquals(validation, storage.restoreResult(academyId, oldId).validationResult());
    }

    @Test
    void v11ReplayDoesNotOverwriteTeacherEditsAndOriginalArchiveIsImmutable() throws Exception {
        var input = v11Case("advanced-structure-multiline");
        long id = saveV11(input);
        var initial = storage.restoreResult(academyId, id);
        long cardId = initial.correctionLinks().getFirst().correctionId();
        tx.executeWithoutResult(status -> corrections
                .findById(cardId)
                .orElseThrow()
                .update(loadCard(cardId).getContent(), null, false));
        assertNotNull(loadCard(cardId).getV11Details().edit(), "동일 내용 재전송은 동작을 무효화하지 않는다.");
        tx.executeWithoutResult(
                status -> corrections.findById(cardId).orElseThrow().editContent("teacher changed text"));
        var edited = loadCard(cardId);
        assertNull(edited.getV11Details().edit());
        assertNotNull(edited.getV11Details().aiInitialEdit());
        assertEquals(id, saveV11(input));
        assertEquals("teacher changed text", loadCard(cardId).getContent());
        assertEquals(initial, storage.restoreResult(academyId, id));
        assertThrows(
                IllegalStateException.class,
                () -> storage.recordValidatedResultV11(
                        academyId,
                        input.generationId(),
                        input.transcriptionId(),
                        input.profile().metadata(),
                        input.raw() + " ",
                        input.validation()));
        tx.executeWithoutResult(
                status -> corrections.findById(cardId).orElseThrow().delete());
        tx.executeWithoutResult(
                status -> corrections.findById(cardId).orElseThrow().restore());
        assertEquals("teacher changed text", loadCard(cardId).getContent());
        assertEquals(initial, storage.restoreResult(academyId, id));
    }

    @Test
    void v11CardsAndArchiveRollBackTogetherWhileCommittedTranscriptionRemains() throws Exception {
        var input = v11Case("advanced-structure-multiline");
        tx.executeWithoutResult(status -> {
            saveV11(input);
            entityManager.flush();
            assertEquals(1, count("corrections"));
            assertEquals(1, count("worksheet_generation_results"));
            status.setRollbackOnly();
        });
        assertEquals(0, count("corrections"));
        assertEquals(0, count("worksheet_generation_results"));
        assertNotNull(storage.restoreTranscription(academyId, input.transcriptionId()));
    }

    @Test
    void v11UnreadableResponsesUseTheirOwnParserAndArePreservedIdempotently() throws Exception {
        var input = v11Case("standard-grammar-replace");
        String broken = "\n{invalid json}\n확인 필요\n";
        long id = storage.recordUnreadableResult(
                academyId,
                input.generationId(),
                input.transcriptionId(),
                input.profile().metadata(),
                broken,
                "v1.1 구조 오류");
        var restored = storage.restoreResult(academyId, id);
        assertEquals(GenerationResultStatus.UNREADABLE, restored.status());
        assertEquals(broken, restored.rawResponse());
        assertNull(restored.versionedValidationResult());
        assertNull(restored.validationResultV11());
        assertTrue(restored.correctionLinks().isEmpty());
        assertEquals(
                id,
                storage.recordUnreadableResult(
                        academyId,
                        input.generationId(),
                        input.transcriptionId(),
                        input.profile().metadata(),
                        broken,
                        "v1.1 구조 오류"));
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.recordUnreadableResult(
                        academyId,
                        input.generationId(),
                        input.transcriptionId(),
                        input.profile().metadata(),
                        input.raw(),
                        "가짜 구조 오류"));
        assertThrows(IllegalStateException.class, () -> saveV11(input));
        assertEquals(0, count("corrections"));
    }

    @Test
    void v11RejectsWrongContractProfileValidationAndTenantBeforeCreatingCards() throws Exception {
        var input = v11Case("standard-grammar-replace");
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.recordValidatedResult(
                        academyId,
                        input.generationId(),
                        input.transcriptionId(),
                        input.profile().metadata(),
                        input.raw(),
                        validation));
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.recordValidatedResultV11(
                        academyId,
                        input.generationId(),
                        input.transcriptionId(),
                        META,
                        input.raw(),
                        input.validation()));
        var forged = new com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationResult(
                input.validation().originalResponse(),
                input.validation().context(),
                List.of(),
                input.validation().summary());
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.recordValidatedResultV11(
                        academyId,
                        input.generationId(),
                        input.transcriptionId(),
                        input.profile().metadata(),
                        input.raw(),
                        forged));
        assertThrows(
                CustomException.class,
                () -> storage.recordValidatedResultV11(
                        otherAcademyId,
                        input.generationId(),
                        input.transcriptionId(),
                        input.profile().metadata(),
                        input.raw(),
                        input.validation()));
        assertEquals(0, count("corrections"));
        assertEquals(0, count("worksheet_generation_results"));
    }

    @Test
    void databaseConstraintKeepsV1StrictAndAllowsOnlyVersionedMultilineCards() throws Exception {
        var input = v11Case("advanced-structure-multiline");
        long result = saveV11(input);
        long cardId = storage.restoreResult(academyId, result)
                .correctionLinks()
                .getFirst()
                .correctionId();
        assertNull(loadCard(cardId).getAnchor());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("update corrections set v11_details=null where id=?", cardId));
        assertNotNull(loadCard(cardId).getV11Details());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("update corrections set action='REVIEW' where id=?", cardId));
    }

    @Test
    void restoreRejectsUnknownStoredContractOrValidationVersionsWithoutFallback() throws Exception {
        var input = v11Case("standard-grammar-replace");
        long id = saveV11(input);
        jdbc.update("update worksheet_generation_results set contract_version=? where id=?", "unknown-contract", id);
        assertThrows(IllegalArgumentException.class, () -> storage.restoreResult(academyId, id));
        jdbc.update(
                "update worksheet_generation_results set contract_version=?, validation_version=? where id=?",
                input.profile().metadata().contractVersion(),
                "unknown-validator",
                id);
        assertThrows(IllegalArgumentException.class, () -> storage.restoreResult(academyId, id));
    }

    private record V11Case(
            Long generationId,
            Long transcriptionId,
            GenerationAiProfile profile,
            String raw,
            com.ceos24.autedi.domain.generation.contract.v1_1.validation.AiValidationResult validation) {}

    private static java.util.stream.Stream<String> acceptedV11Cases() throws Exception {
        return JSON.readTree(resource("/ai/contract/v1.1/cases.json"))
                .get("cases")
                .valueStream()
                .filter(value ->
                        "ACCEPT".equals(value.at("/expected/parse_result").asString()))
                .map(value -> value.get("id").asString());
    }

    private V11Case v11Case(String fixtureId) throws Exception {
        JsonNode fixture = JSON.readTree(resource("/ai/contract/v1.1/cases.json"))
                .get("cases")
                .valueStream()
                .filter(value -> value.get("id").asString().equals(fixtureId))
                .findFirst()
                .orElseThrow();
        List<String> lines = new ArrayList<>();
        fixture.at("/context/transcription_lines")
                .forEach(line -> lines.add(line.get("text").asString()));
        Map<AiArea, AiCorrectionLevel> selected = new EnumMap<>(AiArea.class);
        fixture.at("/context/selected_areas")
                .forEach(setting -> selected.put(
                        AiArea.valueOf(setting.get("area").asString()),
                        AiCorrectionLevel.valueOf(setting.get("level").asString())));
        var profile = GenerationClientProfiles.profile(
                GenerationClientMode.TERRA, GenerationContractVersion.V1_1, AiCorrectionPromptVariant.DEFAULT);
        long generation = newGeneration(worksheetId, selected, profile);
        long transcription = storage.recordTranscription(
                academyId, generation, image, lines, profile.metadata().model());
        String response =
                resource("/ai/contract/v1.1/" + fixture.get("response_file").asString());
        if (fixtureId.equals("advanced-structure-multiline")) response = "\n" + " ".repeat(70_000) + response + "\n  ";
        var checked = (GenerationContractRouter.V11Validation)
                contracts.parseAndValidate(profile, response, new GenerationContractRouter.Input(1, lines, selected));
        return new V11Case(generation, transcription, profile, response, checked.result());
    }

    private long saveV11(V11Case value) {
        return storage.recordValidatedResultV11(
                academyId,
                value.generationId(),
                value.transcriptionId(),
                value.profile().metadata(),
                value.raw(),
                value.validation());
    }

    private Long transcript() {
        return storage.recordTranscription(academyId, generationId, image, LINES, META.model());
    }

    private Long newGeneration(Long worksheetId, Map<AiArea, AiCorrectionLevel> selected) {
        return newGeneration(worksheetId, selected, null);
    }

    private Long newGeneration(Long worksheetId, Map<AiArea, AiCorrectionLevel> selected, GenerationAiProfile profile) {
        return tx.execute(status -> {
            var worksheet = worksheetRepository.findById(worksheetId).orElseThrow();
            List<Map<String, String>> snapshot = selected.entrySet().stream()
                    .map(entry -> Map.of(
                            "area",
                            entry.getKey().name(),
                            "level",
                            entry.getValue().name()))
                    .toList();
            return generations
                    .save(WorksheetGeneration.builder()
                            .worksheet(worksheet)
                            .requestedBy(users.findById(userId).orElseThrow())
                            .idempotencyKey(UUID.randomUUID().toString())
                            .requestFileHash(worksheet.getFileHash())
                            .requestSettingsSnapshot(snapshot)
                            .clientMode(profile == null ? null : GenerationClientMode.TERRA)
                            .requestAiProfile(profile)
                            .build())
                    .getId();
        });
    }

    private Correction card(StoredGenerationResult result, int index) {
        return loadCard(result.correctionLinks().stream()
                .filter(link -> link.candidateIndex() == index)
                .findFirst()
                .orElseThrow()
                .correctionId());
    }

    private Correction loadCard(Long id) {
        return tx.execute(status -> corrections.findById(id).orElseThrow());
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private static AiValidationResult validate(String raw, Map<AiArea, AiCorrectionLevel> selected) {
        return new AiCorrectionResponseValidator()
                .validate(new AiCorrectionResponseParser().parse(raw), new AiValidationContext(1, LINES, selected));
    }

    private void assertLocalH2() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(
                    connection.getMetaData().getURL().startsWith("jdbc:h2:mem:autedi_archive_storage"),
                    "저장 테스트는 전용 메모리 H2에서만 실행해야 합니다.");
        }
    }

    private static String resource(String path) throws Exception {
        try (var stream = GenerationResultStorageTest.class.getResourceAsStream(path)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
