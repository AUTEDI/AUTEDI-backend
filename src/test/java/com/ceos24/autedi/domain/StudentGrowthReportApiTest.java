package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.ceos24.autedi.domain.generation.contract.v1.*;
import com.ceos24.autedi.domain.generation.contract.v1.validation.*;
import com.ceos24.autedi.domain.generation.entity.*;
import com.ceos24.autedi.domain.generation.persistence.GenerationResultMetadata;
import com.ceos24.autedi.domain.generation.repository.*;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.*;
import com.ceos24.autedi.domain.worksheet.repository.*;
import com.ceos24.autedi.domain.worksheet.service.WorksheetSavedSnapshot;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:b8_api;DB_CLOSE_DELAY=-1;MODE=MySQL")
@Transactional
class StudentGrowthReportApiTest {
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
    WorksheetTranscriptionRepository transcriptions;

    @Autowired
    WorksheetGenerationResultRepository generationResults;

    @Autowired
    CorrectionRepository corrections;

    @Autowired
    CommentRepository comments;

    @Autowired
    WorksheetSavedResultRepository savedResults;

    @Autowired
    CorrectionService reviewService;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;
    private User owner, peer, director, foreign;
    private Student student;
    private Worksheet worksheet;
    private WorksheetPage page;
    private WorksheetGeneration generation;
    private WorksheetTranscription transcription;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        var academy = academies.saveAndFlush(Academy.builder().name("B8").build());
        owner = user(academy, Role.TEACHER);
        peer = user(academy, Role.TEACHER);
        director = user(academy, Role.DIRECTOR);
        foreign = user(academies.saveAndFlush(Academy.builder().name("other").build()), Role.TEACHER);
        student = students.saveAndFlush(
                Student.builder().academy(academy).name("학생").grade("초3").build());
        prepareWorksheet();
    }

    private void prepareWorksheet() {
        var academy = owner.getAcademy();
        worksheet = worksheets.saveAndFlush(Worksheet.builder()
                .academy(academy)
                .user(owner)
                .originalFilename("b8.png")
                .fileUrl("/b8.png")
                .fileType(FileType.PNG)
                .fileSize(100L)
                .fileHash(UUID.randomUUID().toString())
                .build());
        worksheet.updateMetadata("영어 작문", student);
        worksheet.updateStatus(WorksheetStatus.READY);
        page = pages.saveAndFlush(WorksheetPage.builder()
                .worksheet(worksheet)
                .pageNumber(1)
                .previewImageUrl("/preview.png")
                .width(1000)
                .height(1400)
                .build());
        generation = generations.saveAndFlush(WorksheetGeneration.builder()
                .worksheet(worksheet)
                .requestedBy(owner)
                .idempotencyKey(UUID.randomUUID().toString())
                .requestFileHash(worksheet.getFileHash())
                .requestSettingsSnapshot(Map.of())
                .build());
        generation.succeed();
        transcription = transcriptions.save(new WorksheetTranscription(
                generation, page, "image-hash", "test", List.of("one two three four five six seven eight nine ten")));
        var validation = new AiCorrectionResponseValidator()
                .validate(
                        new AiCorrectionResponse(List.of()),
                        new AiValidationContext(
                                1,
                                transcription.getLines(),
                                Map.of(
                                        AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD,
                                        AiArea.VOCABULARY_EXPRESSION, AiCorrectionLevel.STANDARD)));
        generationResults.save(new WorksheetGenerationResult(
                generation,
                transcription,
                GenerationResultMetadata.current(),
                "{\"items\":[]}",
                validation,
                List.of(),
                null));
        em.flush();
    }

    @Test
    void reportsChronologicalPointsAndPerAreaAvailabilityWithoutMixingOtherStudents() throws Exception {
        var date = LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay();
        freeze(date);
        Long first = worksheet.getId();
        prepareWorksheet();
        freeze(date);
        Long second = worksheet.getId();
        prepareWorksheet();
        freeze(date.minusDays(1));
        Long earlier = worksheet.getId();
        var originalStudent = student;
        student = students.saveAndFlush(Student.builder()
                .academy(owner.getAcademy())
                .name("다른 학생")
                .grade("초3")
                .build());
        prepareWorksheet();
        freeze(date);
        student = originalStudent;
        var data = data(report(peer).andExpect(status().isOk()));
        assertThat(data.path("points")
                        .valueStream()
                        .map(p -> p.path("worksheetId").asLong())
                        .toList())
                .containsExactly(earlier, first, second);
        assertThat(data.path("dataStatus").asText()).isEqualTo("AVAILABLE");
        assertThat(data.at("/series/0/comparablePointCount").asInt()).isEqualTo(3);
        assertThat(data.at("/series/2/dataStatus").asText()).isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    void pointsFromDifferentAreasDoNotPretendToProvideTwoComparablePoints() throws Exception {
        var date = LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay();
        freeze(date);
        keepSummaryArea("GRAMMAR_SPELLING");
        prepareWorksheet();
        freeze(date);
        keepSummaryArea("VOCABULARY_EXPRESSION");
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points.length()").value(2))
                .andExpect(jsonPath("$.result.comparablePointCount").value(1))
                .andExpect(jsonPath("$.result.dataStatus").value("INSUFFICIENT_DATA"));
    }

    @SuppressWarnings("unchecked")
    private void keepSummaryArea(String area) {
        var saved = savedResults.findById(worksheet.getId()).orElseThrow();
        var review = (Map<String, Object>) saved.getSnapshot().get("review");
        review.put(
                "summary",
                ((List<Map<String, Object>>) review.get("summary"))
                        .stream().filter(row -> area.equals(row.get("area"))).toList());
        em.flush();
    }

    @Test
    void retryUsesTheOriginalTranscriptionActuallyReferencedByTheSuccessfulResult() throws Exception {
        var retry = generations.saveAndFlush(WorksheetGeneration.builder()
                .worksheet(worksheet)
                .requestedBy(owner)
                .idempotencyKey(UUID.randomUUID().toString())
                .requestFileHash(worksheet.getFileHash())
                .requestSettingsSnapshot(Map.of())
                .build());
        retry.succeed();
        var validation = new AiCorrectionResponseValidator()
                .validate(
                        new AiCorrectionResponse(List.of()),
                        new AiValidationContext(
                                1,
                                transcription.getLines(),
                                Map.of(AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD)));
        generationResults.save(new WorksheetGenerationResult(
                retry,
                transcription,
                GenerationResultMetadata.current(),
                "{\"items\":[]}",
                validation,
                List.of(),
                null));
        generation = retry;
        card(FeedbackType.CORRECTION, CorrectionArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR));
        completeApi();
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points[0].wordCount").value(10))
                .andExpect(jsonPath("$.result.points[0].areas[0].errorsPer100Words")
                        .value(10.0));
    }

    @Test
    void legacyCardsRetainErrorCountsButDoNotInventIssueKindsAndExamplesAreBounded() throws Exception {
        for (int i = 0; i < 5; i++)
            corrections.saveAndFlush(Correction.builder()
                    .worksheet(worksheet)
                    .page(page)
                    .generation(generation)
                    .area(CorrectionArea.GRAMMAR_MECHANICS)
                    .originalText("one")
                    .aiInitialContent("a word")
                    .explanation("legacy")
                    .regions(regions())
                    .build());
        completeApi();
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points[0].errorCount").value(5))
                .andExpect(jsonPath("$.result.points[0].unclassifiedIssueCount").value(5))
                .andExpect(
                        jsonPath("$.result.points[0].issueKindCounts[0].count").value(0))
                .andExpect(jsonPath("$.result.points[0].areas[0].representativeFeedback.length()")
                        .value(3));
    }

    @Test
    void countsOnlyConfirmedErrorsAndSeparatesSuggestionsCommentsAndMultilabelKinds() throws Exception {
        var kept = card(
                FeedbackType.CORRECTION,
                CorrectionArea.GRAMMAR_SPELLING,
                List.of(AiIssueKind.GRAMMAR, AiIssueKind.PUNCTUATION));
        card(FeedbackType.CORRECTION, CorrectionArea.GRAMMAR_SPELLING, List.of(AiIssueKind.SPELLING))
                .delete();
        card(FeedbackType.CORRECTION, CorrectionArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR))
                .updateApplied(false);
        card(FeedbackType.SUGGESTION, CorrectionArea.VOCABULARY_EXPRESSION, List.of(AiIssueKind.VOCABULARY));
        comment(new CommentClassification(
                CommentFeedbackType.CORRECTION, CorrectionArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR), null));
        comment(new CommentClassification(
                CommentFeedbackType.SUGGESTION, CorrectionArea.CONTENT_SUMMARY, List.of(AiIssueKind.CONTENT), null));
        comment(CommentClassification.general());
        comment(new CommentClassification(CommentFeedbackType.SUPPLEMENT, null, null, kept.getId()));
        comment(new CommentClassification(
                        CommentFeedbackType.CORRECTION,
                        CorrectionArea.GRAMMAR_SPELLING,
                        List.of(AiIssueKind.SPELLING),
                        null))
                .delete();
        completeApi();
        var data = data(report(owner).andExpect(status().isOk()));
        var point = data.at("/points/0");
        assertThat(point.path("errorCount").asInt()).isEqualTo(2);
        assertThat(point.path("aiErrorCount").asInt()).isEqualTo(1);
        assertThat(point.path("teacherErrorCount").asInt()).isEqualTo(1);
        assertThat(point.path("suggestionCount").asInt()).isEqualTo(2);
        assertThat(point.path("generalCommentCount").asInt()).isEqualTo(1);
        assertThat(point.path("supplementCommentCount").asInt()).isEqualTo(1);
        assertThat(point.path("wordCount").asInt()).isEqualTo(10);
        assertThat(point.at("/areas/0/errorsPer100Words").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(point.at("/areas/0/errorCount").asInt()).isEqualTo(2);
        assertThat(point.at("/areas/0/issueKindCounts/0/count").asInt()).isEqualTo(2);
        assertThat(point.at("/areas/0/issueKindCounts/2/count").asInt()).isEqualTo(1);
        assertThat(point.at("/areas/0/representativeFeedback").size()).isEqualTo(2);
        assertThat(point.at("/areas/1/errorCount").asInt()).isZero();
        assertThat(point.at("/areas/1/errorsPer100Words").decimalValue()).isEqualByComparingTo("0");
        assertThat(point.at("/areas/2/analysisStatus").asText()).isEqualTo("NOT_ANALYZED");
        assertThat(point.at("/areas/2/errorCount").isNull()).isTrue();
        assertThat(point.at("/areas/3/analysisStatus").asText()).isEqualTo("TEACHER_ONLY");
        assertThat(point.at("/areas/3/errorsPer100Words").isNull()).isTrue();
        assertThat(data.path("dataStatus").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(savedResults.findById(worksheet.getId()).orElseThrow().getSchemaVersion())
                .isEqualTo(2);
    }

    @Test
    void reportUsesSavedFeedbackEvenIfLiveRowsAreChangedAndCompletionBlocksClassificationEdits() throws Exception {
        var c = comment(new CommentClassification(
                CommentFeedbackType.CORRECTION, CorrectionArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR), null));
        var card = card(FeedbackType.CORRECTION, CorrectionArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR));
        completeApi();
        var before = data(report(peer).andExpect(status().isOk()));
        call(
                        patch(base() + "/comments/" + c.getId())
                                .header(HttpHeaders.IF_MATCH, c.getEtag())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"수정\",\"classification\":{\"type\":\"GENERAL\"}}"),
                        owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSHEET_003"));
        // 현재 테이블을 집계하지 않는다는 것을 검증하기 위한 직접 변경.
        card.delete();
        c.changeClassification(CommentClassification.general());
        em.flush();
        assertThat(data(report(peer).andExpect(status().isOk()))).isEqualTo(before);
    }

    @Test
    void unsavedAndDraftsAreExcludedAndActualZeroIsAvailable() throws Exception {
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.dataStatus").value("EMPTY"));
        call(post(base() + "/save-draft"), owner).andExpect(status().isOk());
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points").isEmpty());
        completeApi();
        var point = data(report(owner).andExpect(status().isOk())).at("/points/0");
        assertThat(point.path("errorCount").asInt()).isZero();
        assertThat(point.at("/areas/0/analysisStatus").asText()).isEqualTo("ANALYZED");
        assertThat(point.at("/areas/0/errorsPer100Words").decimalValue()).isEqualByComparingTo("0");
        jdbc.update("update worksheets set deleted_at=current_timestamp where id=?", worksheet.getId());
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.dataStatus").value("EMPTY"));
    }

    @Test
    void commentsClassificationIsValidatedPreservedAndRestoredWithDraft() throws Exception {
        var created = data(call(
                        post(base() + "/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        commentBody(
                                                "{\"type\":\"CORRECTION\",\"area\":\"GRAMMAR_SPELLING\",\"issueKinds\":[\"grammar\"]}")),
                        owner)
                .andExpect(status().isCreated()));
        Long id = created.path("id").asLong();
        call(post(base() + "/save-draft"), owner).andExpect(status().isOk());
        String tag = created.path("etag").asText();
        var changed = data(call(
                        patch(base() + "/comments/" + id)
                                .header(HttpHeaders.IF_MATCH, tag)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"일반\",\"classification\":{\"type\":\"GENERAL\"}}"),
                        owner)
                .andExpect(status().isOk()));
        assertThat(changed.at("/classification/type").asText()).isEqualTo("GENERAL");
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, worksheet.getEtag()), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.comments[0].classification.type").value("CORRECTION"));
        var c = comments.findById(id).orElseThrow();
        call(
                        patch(base() + "/comments/" + id)
                                .header(HttpHeaders.IF_MATCH, c.getEtag())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"분류 유지\"}"),
                        owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.classification.type").value("CORRECTION"));
        call(
                        patch(base() + "/comments/" + id)
                                .header(HttpHeaders.IF_MATCH, tag)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"old\"}"),
                        owner)
                .andExpect(status().isPreconditionFailed());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"type\":\"CORRECTION\"}",
                "{\"type\":\"CORRECTION\",\"area\":\"GRAMMAR_SPELLING\",\"issueKinds\":[\"grammar\",\"grammar\"]}",
                "{\"type\":\"CORRECTION\",\"area\":\"GRAMMAR_SPELLING\",\"issueKinds\":[\"ocr_mismatch\"]}",
                "{\"type\":\"CORRECTION\",\"area\":\"GRAMMAR_SPELLING\",\"issueKinds\":[\"content\"]}",
                "{\"type\":\"CORRECTION\",\"area\":\"GRAMMAR_SPELLING\",\"issueKinds\":[null]}",
                "{\"type\":\"GENERAL\",\"area\":\"GRAMMAR_SPELLING\"}",
                "{\"type\":\"GENERAL\",\"correctionId\":1}",
                "{\"type\":\"SUPPLEMENT\"}",
                "{\"type\":\"SUPPLEMENT\",\"correctionId\":0}",
                "{}"
            })
    void rejectsInvalidCommentClassification(String classification) throws Exception {
        call(
                        post(base() + "/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(commentBody(classification)),
                        owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void supplementMustReferenceActiveCardInSameWorksheetAndDoesNotPromoteDeletedCardToError() throws Exception {
        var card = card(FeedbackType.CORRECTION, CorrectionArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR));
        var body = commentBody("{\"type\":\"SUPPLEMENT\",\"correctionId\":" + card.getId() + "}");
        call(post(base() + "/comments").contentType(MediaType.APPLICATION_JSON).content(body), owner)
                .andExpect(status().isCreated());
        card.delete();
        em.flush();
        call(post(base() + "/comments").contentType(MediaType.APPLICATION_JSON).content(body), owner)
                .andExpect(status().isNotFound());
        completeApi();
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points[0].errorCount").value(0))
                .andExpect(jsonPath("$.result.points[0].supplementCommentCount").value(1));
    }

    @Test
    void oldSnapshotsWithoutClassificationStillReadRestoreAndAggregateAsGeneral() throws Exception {
        var c = comment(CommentClassification.general());
        call(post(base() + "/save-draft"), owner).andExpect(status().isOk());
        var saved = savedResults.findById(worksheet.getId()).orElseThrow();
        var snapshot = saved.getSnapshot();
        ((List<Map<String, Object>>) snapshot.get("comments")).forEach(row -> row.remove("classification"));
        var review = (Map<String, Object>) snapshot.get("review");
        ((List<Map<String, Object>>) review.get("comments")).forEach(row -> row.remove("classification"));
        em.flush();
        jdbc.update("update worksheet_saved_results set schema_version=1 where worksheet_id=?", worksheet.getId());
        em.refresh(saved);
        c.changeClassification(new CommentClassification(
                CommentFeedbackType.CORRECTION, CorrectionArea.GRAMMAR_SPELLING, List.of(AiIssueKind.GRAMMAR), null));
        em.flush();
        call(get(base() + "/saved-result"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.review.comments[0].classification.type")
                        .value("GENERAL"));
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, worksheet.getEtag()), owner)
                .andExpect(status().isOk());
        assertThat(c.getClassification().type()).isEqualTo(CommentFeedbackType.GENERAL);
        completeApi();
        // 최종 저장본도 같은 v1 호환 규칙을 사용한다.
        saved = savedResults.findById(worksheet.getId()).orElseThrow();
        ((List<Map<String, Object>>) ((Map<String, Object>) saved.getSnapshot().get("review")).get("comments"))
                .forEach(row -> row.remove("classification"));
        em.flush();
        jdbc.update("update worksheet_saved_results set schema_version=1 where worksheet_id=?", worksheet.getId());
        em.refresh(saved);
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points[0].generalCommentCount").value(1))
                .andExpect(jsonPath("$.result.points[0].teacherErrorCount").value(0));
    }

    @Test
    void missingAndZeroWordTranscriptionsNeverBecomeZeroRates() throws Exception {
        completeApi();
        jdbc.update("update worksheet_transcriptions set transcription_text='!!!' where id=?", transcription.getId());
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points[0].wordCount").value(0))
                .andExpect(jsonPath("$.result.points[0].wordCountStatus").value("EMPTY_TRANSCRIPTION"))
                .andExpect(jsonPath("$.result.points[0].areas[0].errorsPer100Words")
                        .isEmpty());
        var saved = savedResults.findById(worksheet.getId()).orElseThrow();
        ((Map<String, Object>) saved.getSnapshot().get("review")).put("aiResults", List.of());
        em.flush();
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points[0].wordCountStatus").value("MISSING_TRANSCRIPTION"))
                .andExpect(jsonPath("$.result.points[0].areas[0].errorsPer100Words")
                        .isEmpty());
    }

    @Test
    void corruptSnapshotIsVisibleAsGapAndNeverReportedAsZeroErrors() throws Exception {
        completeApi();
        jdbc.update("update worksheet_saved_results set schema_version=999 where worksheet_id=?", worksheet.getId());
        em.clear();
        report(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.unavailableResultCount").value(1))
                .andExpect(jsonPath("$.result.points[0].aggregationStatus").value("UNREADABLE_SNAPSHOT"))
                .andExpect(jsonPath("$.result.points[0].errorCount").isEmpty());
    }

    @Test
    void sameAcademyPeersAndDirectorReadButForeignAndUnknownStudentReturnSame404() throws Exception {
        completeApi();
        report(peer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points.length()").value(1));
        report(director).andExpect(status().isOk());
        report(foreign)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"));
        call(get("/api/v1/students/999999/growth-report"), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"));
        mvc.perform(get(reportPath())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1M", "3M", "6M", "ALL"})
    void supportsPeriodsAndCanonicalAreaFilter(String period) throws Exception {
        completeApi();
        call(get(reportPath()).param("period", period).param("area", "GRAMMAR_MECHANICS"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.period").value(period))
                .andExpect(jsonPath("$.result.selectedArea").value("GRAMMAR_SPELLING"))
                .andExpect(jsonPath("$.result.points[0].areas.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1m", "2M", "year", "0"})
    void invalidPeriodIs400(String period) throws Exception {
        call(get(reportPath()).param("period", period), owner).andExpect(status().isBadRequest());
    }

    @Test
    void invalidAreaIs400() throws Exception {
        call(get(reportPath()).param("area", "unknown"), owner).andExpect(status().isBadRequest());
    }

    @Test
    void periodUsesCompletionDateIncludesBoundaryAndAllKeepsChronologicalOrder() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        freeze(today.minusMonths(1).atStartOfDay());
        call(get(reportPath()).param("period", "1M"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points.length()").value(1));
        var saved = savedResults.findById(worksheet.getId()).orElseThrow();
        var old = today.minusMonths(1).atStartOfDay().minusNanos(1000);
        ((Map<String, Object>) saved.getSnapshot().get("review")).put("worksheet", metadataWithCompletion(saved, old));
        em.flush();
        jdbc.update("update worksheets set completed_at=? where id=?", old, worksheet.getId());
        em.refresh(worksheet);
        call(get(reportPath()).param("period", "1M"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points").isEmpty());
        call(get(reportPath()).param("period", "ALL"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points.length()").value(1));
    }

    private Map<String, Object> metadataWithCompletion(WorksheetSavedResult saved, LocalDateTime date) {
        var metadata =
                (Map<String, Object>) ((Map<String, Object>) saved.getSnapshot().get("review")).get("worksheet");
        metadata.put("completedAt", date.toString());
        return metadata;
    }

    private void freeze(LocalDateTime date) {
        worksheet.complete(owner, date.truncatedTo(ChronoUnit.MICROS));
        em.flush();
        var snapshot = new WorksheetSavedSnapshot(
                reviewService.getReview(owner.getAcademy().getId(), worksheet.getId()),
                corrections.findAllByWorksheetIdOrderByIdAsc(worksheet.getId()).stream()
                        .map(WorksheetSavedSnapshot.CorrectionEdit::from)
                        .toList(),
                comments.findAllByWorksheetIdOrderByIdAsc(worksheet.getId()).stream()
                        .map(WorksheetSavedSnapshot.CommentEdit::from)
                        .toList());
        var saved = new WorksheetSavedResult(worksheet);
        saved.replace(
                generation, owner, date, json.convertValue(snapshot, new TypeReference<Map<String, Object>>() {}));
        savedResults.saveAndFlush(saved);
    }

    private void completeApi() throws Exception {
        em.flush();
        call(post(base() + "/complete").header(HttpHeaders.IF_MATCH, worksheet.getEtag()), owner)
                .andExpect(status().isOk());
    }

    private Correction card(FeedbackType type, CorrectionArea area, List<AiIssueKind> kinds) {
        return corrections.saveAndFlush(Correction.builder()
                .worksheet(worksheet)
                .page(page)
                .generation(generation)
                .area(area)
                .originalText("one")
                .aiInitialContent("a word")
                .explanation("why")
                .regions(regions())
                .action(AiAction.REPLACE)
                .feedbackType(type)
                .issueKinds(kinds)
                .anchor(new CorrectionAnchor(transcription.getId(), 1, 1, 0, 3))
                .locationUncertain(false)
                .warnings(List.of())
                .requiresReview(false)
                .build());
    }

    private Comment comment(CommentClassification classification) {
        var c = Comment.builder()
                .worksheet(worksheet)
                .page(page)
                .user(owner)
                .highlightType(HighlightType.HIGHLIGHT)
                .regions(regions())
                .content("teacher feedback")
                .build();
        c.changeClassification(classification);
        return comments.saveAndFlush(c);
    }

    private List<RegionPayload> regions() {
        return List.of(new RegionPayload(page.getId(), .1, .1, .1, .1, null));
    }

    private String commentBody(String classification) {
        return "{\"pageId\":" + page.getId()
                + ",\"highlightType\":\"HIGHLIGHT\",\"content\":\"교사 의견\",\"regions\":[{\"pageId\":" + page.getId()
                + ",\"x\":0.1,\"y\":0.1,\"width\":0.1,\"height\":0.1}],\"classification\":" + classification + "}";
    }

    private User user(Academy academy, Role role) {
        return users.saveAndFlush(User.builder()
                .academy(academy)
                .role(role)
                .name(role.name())
                .email(UUID.randomUUID() + "@test.com")
                .passwordHash("hash")
                .build());
    }

    private String base() {
        return "/api/v1/worksheets/" + worksheet.getId();
    }

    private String reportPath() {
        return "/api/v1/students/" + student.getId() + "/growth-report";
    }

    private ResultActions report(User user) throws Exception {
        return call(get(reportPath()), user);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, User user) throws Exception {
        return mvc.perform(request.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer "
                        + tokens.createAccessToken(
                                user.getId(),
                                user.getAcademy().getId(),
                                user.getEmail(),
                                user.getRole().name())));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString())
                .path("result");
    }
}
