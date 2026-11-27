package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.comment.entity.Comment;
import com.ceos24.autedi.domain.comment.entity.HighlightType;
import com.ceos24.autedi.domain.comment.repository.CommentRepository;
import com.ceos24.autedi.domain.correction.dto.request.CorrectionUpdateRequest;
import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionAnchor;
import com.ceos24.autedi.domain.correction.entity.CorrectionV11Details;
import com.ceos24.autedi.domain.correction.entity.FeedbackType;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.correction.service.CorrectionService;
import com.ceos24.autedi.domain.generation.contract.v1.AiAction;
import com.ceos24.autedi.domain.generation.contract.v1.AiIssueKind;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiEdit;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiEditOperation;
import com.ceos24.autedi.domain.generation.contract.v1_1.AiRevisionScope;
import com.ceos24.autedi.domain.generation.entity.WorksheetGeneration;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSavedResultRepository;
import com.ceos24.autedi.domain.worksheet.service.WorksheetSavedResultService;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 보안·MVC·JSON·커밋된 DB를 통과해 마지막 저장본과 작업본의 분리를 검증한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:b5_api;DB_CLOSE_DELAY=-1;MODE=MySQL")
class WorksheetSavedResultApiTest {
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
    WorksheetSavedResultRepository savedResults;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CorrectionService correctionService;

    @Autowired
    WorksheetSavedResultService saveService;

    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;
    private TransactionTemplate tx;
    private Long worksheetId, pageId, correctionId, deletedCorrectionId, commentId, deletedCommentId;
    private User owner, peer, director, foreign;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            Academy academy =
                    academies.saveAndFlush(Academy.builder().name("B5").build());
            owner = user(academy, Role.TEACHER);
            peer = user(academy, Role.TEACHER);
            director = user(academy, Role.DIRECTOR);
            foreign = user(
                    academies.saveAndFlush(Academy.builder().name("foreign").build()), Role.TEACHER);
            Student student = students.saveAndFlush(
                    Student.builder().academy(academy).name("김학생").grade("초3").build());
            Worksheet worksheet = worksheets.saveAndFlush(Worksheet.builder()
                    .academy(academy)
                    .user(owner)
                    .originalFilename("b5.png")
                    .fileUrl("/b5.png")
                    .fileType(FileType.PNG)
                    .fileSize(100L)
                    .fileHash("b5-file")
                    .build());
            worksheet.updateMetadata("11월 작문", student);
            worksheet.updateStatus(WorksheetStatus.READY);
            WorksheetPage page = pages.saveAndFlush(WorksheetPage.builder()
                    .worksheet(worksheet)
                    .pageNumber(1)
                    .previewImageUrl("/b5-preview.png")
                    .width(1000)
                    .height(1400)
                    .build());
            WorksheetGeneration generation = generations.saveAndFlush(WorksheetGeneration.builder()
                    .worksheet(worksheet)
                    .requestedBy(owner)
                    .idempotencyKey(UUID.randomUUID().toString())
                    .requestFileHash("b5-file")
                    .requestSettingsSnapshot(Map.of())
                    .build());
            worksheetId = worksheet.getId();
            pageId = page.getId();
            Correction card = corrections.saveAndFlush(card(worksheet, page, generation));
            correctionId = card.getId();
            Correction removed = corrections.saveAndFlush(card(worksheet, page, generation));
            removed.delete();
            deletedCorrectionId = removed.getId();
            commentId = comments.saveAndFlush(comment(worksheet, page, "saved comment"))
                    .getId();
            Comment removedComment = comments.saveAndFlush(comment(worksheet, page, "deleted comment"));
            removedComment.delete();
            deletedCommentId = removedComment.getId();
        });
    }

    @Test
    void saveFreezesResultAndRepeatSaveReplacesOnlyLatest() throws Exception {
        JsonNode first = result(save(owner, etag()).andExpect(status().isOk()));
        assertThat(first.path("revision").asLong()).isEqualTo(1);
        assertThat(first.path("savedBy").asLong()).isEqualTo(owner.getId());
        assertThat(first.at("/review/worksheet/saveStatus").asText()).isEqualTo("TEMP_SAVED");
        String firstSavedAt = first.at("/review/worksheet/firstSavedAt").asText();
        String liveTag = first.at("/review/worksheet/etag").asText();
        editCorrection("later content");
        assertThat(etag()).isNotEqualTo(liveTag);
        JsonNode frozen = result(call(get(base() + "/saved-result"), owner).andExpect(status().isOk()));
        assertThat(frozen).isEqualTo(first);
        assertThat(frozen.at("/review/corrections/0/content").asText()).isEqualTo("AI content");
        JsonNode second = result(save(owner, etag()).andExpect(status().isOk()));
        assertThat(second.path("revision").asLong()).isEqualTo(2);
        assertThat(second.at("/review/worksheet/firstSavedAt").asText()).isEqualTo(firstSavedAt);
        assertThat(second.at("/review/corrections/0/content").asText()).isEqualTo("later content");
        assertThat(savedResults.findAll().stream()
                        .filter(r -> r.getWorksheetId().equals(worksheetId)))
                .hasSize(1);
        tx.executeWithoutResult(s -> {
            Worksheet w = worksheets.findById(worksheetId).orElseThrow();
            assertThat(w.getUser().getId()).isEqualTo(owner.getId());
            assertThat(w.getLastSavedBy().getId()).isEqualTo(owner.getId());
            assertThat(w.getCompletedAt()).isNull();
        });
    }

    @Test
    void explicitResumeRestoresAllMutableStateAndSoftDeletesNewComments() throws Exception {
        JsonNode saved = result(save(owner, null).andExpect(status().isOk()));
        String savedTag = saved.at("/review/worksheet/etag").asText();
        String cardTag = cardEtag();
        call(
                        patch(base() + "/corrections/" + correctionId)
                                .header(HttpHeaders.IF_MATCH, cardTag)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"changed\",\"isApplied\":false,\"regions\":[{\"pageId\":"
                                        + pageId + ",\"x\":0.2,\"y\":0.2,\"width\":0.1,\"height\":0.1}]}"),
                        owner)
                .andExpect(status().isOk());
        call(delete(base() + "/corrections/" + correctionId).header(HttpHeaders.IF_MATCH, cardEtag()), owner)
                .andExpect(status().isNoContent());
        String deletedTag = tx.execute(
                s -> corrections.findById(deletedCorrectionId).orElseThrow().getEtag());
        call(
                        post(base() + "/corrections/" + deletedCorrectionId + "/restore")
                                .header(HttpHeaders.IF_MATCH, deletedTag),
                        owner)
                .andExpect(status().isOk());
        String commentTag = comments.findById(commentId).orElseThrow().getEtag();
        call(
                        patch(base() + "/comments/" + commentId)
                                .header(HttpHeaders.IF_MATCH, commentTag)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"changed comment\",\"color\":\"RED\"}"),
                        owner)
                .andExpect(status().isOk());
        call(
                        delete(base() + "/comments/" + commentId)
                                .header(
                                        HttpHeaders.IF_MATCH,
                                        comments.findById(commentId)
                                                .orElseThrow()
                                                .getEtag()),
                        owner)
                .andExpect(status().isNoContent());
        JsonNode newComment = result(call(
                        post(base() + "/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pageId\":" + pageId
                                        + ",\"highlightType\":\"HIGHLIGHT\",\"content\":\"new\",\"regions\":[{\"pageId\":"
                                        + pageId + ",\"x\":0.1,\"y\":0.1,\"width\":0.1,\"height\":0.1}]}"),
                        owner)
                .andExpect(status().isCreated()));
        // 저장본 조회는 현재 작업본을 바꾸지 않는다.
        result(call(get(base() + "/saved-result"), owner).andExpect(status().isOk()));
        assertThat(comments.findById(commentId).orElseThrow().isDeleted()).isTrue();
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, savedTag), owner)
                .andExpect(status().isPreconditionFailed());
        JsonNode resumed = result(call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), owner)
                .andExpect(status().isOk()));
        assertThat(resumed.at("/corrections/0/content").asText()).isEqualTo("AI content");
        assertThat(resumed.at("/corrections/0/isApplied").asBoolean()).isTrue();
        assertThat(resumed.at("/corrections/0/editedByTeacher").asBoolean()).isFalse();
        assertThat(resumed.at("/corrections/0/regions/0/x").asDouble()).isEqualTo(0.1);
        assertThat(resumed.path("corrections").size()).isEqualTo(1);
        assertThat(resumed.at("/comments/0/content").asText()).isEqualTo("saved comment");
        assertThat(resumed.at("/comments/0/color").asText()).isEqualTo("BLUE");
        assertThat(resumed.path("comments").size()).isEqualTo(1);
        assertThat(comments.findById(newComment.path("id").asLong())
                        .orElseThrow()
                        .isDeleted())
                .isTrue();
        assertThat(comments.findById(deletedCommentId).orElseThrow().isDeleted())
                .isTrue();
        assertThat(cardEtag()).isNotEqualTo(cardTag);
        editCorrectionWithTag("stale", cardTag).andExpect(status().isPreconditionFailed());
        assertThat(result(call(get(base() + "/saved-result"), owner).andExpect(status().isOk())))
                .isEqualTo(saved);
    }

    @ParameterizedTest
    @ValueSource(strings = {"peer", "director", "foreign"})
    void saveReadAndResumeAreOwnerOnly(String who) throws Exception {
        save(owner, null).andExpect(status().isOk());
        User user = who.equals("peer") ? peer : who.equals("director") ? director : foreign;
        int expected = who.equals("foreign") ? 404 : 403;
        save(user, null).andExpect(status().is(expected));
        call(get(base() + "/saved-result"), user).andExpect(status().is(expected));
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), user)
                .andExpect(status().is(expected));
    }

    @ParameterizedTest
    @EnumSource(
            value = WorksheetStatus.class,
            names = {"DRAFT", "PROCESSING", "FAILED"})
    void onlyReadySupportsB5(WorksheetStatus state) throws Exception {
        save(owner, null).andExpect(status().isOk());
        jdbc.update("update worksheets set status=? where id=?", state.name(), worksheetId);
        save(owner, null).andExpect(status().isConflict());
        call(get(base() + "/saved-result"), owner).andExpect(status().isConflict());
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), owner)
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"completed", "deleted"})
    void closedWorksheetsRejectSaveResumeAndChildEdits(String state) throws Exception {
        save(owner, null).andExpect(status().isOk());
        jdbc.update(
                state.equals("completed")
                        ? "update worksheets set save_status='COMPLETED' where id=?"
                        : "update worksheets set deleted_at=current_timestamp where id=?",
                worksheetId);
        int expected = state.equals("completed") ? 409 : 404;
        save(owner, null).andExpect(status().is(expected));
        call(get(base() + "/saved-result"), owner).andExpect(status().is(expected));
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), owner)
                .andExpect(status().is(expected));
        editCorrectionWithTag("blocked", cardEtag()).andExpect(status().is(expected));
        call(
                        delete(base() + "/comments/" + commentId)
                                .header(
                                        HttpHeaders.IF_MATCH,
                                        comments.findById(commentId)
                                                .orElseThrow()
                                                .getEtag()),
                        owner)
                .andExpect(status().is(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "student_id"})
    void metadataRequiredBeforeFirstSave(String field) throws Exception {
        jdbc.update("update worksheets set " + field + "=null where id=?", worksheetId);
        save(owner, null).andExpect(status().isConflict());
        assertThat(savedResults.existsById(worksheetId)).isFalse();
        assertThat(worksheets.findById(worksheetId).orElseThrow().getSaveStatus())
                .isEqualTo(WorksheetSaveStatus.UNSAVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "*", "\"ws_0-v0\"", "W/\"ws_1-v1\"", ""})
    void resumeRequiresExactCurrentLiveEtag(String tag) throws Exception {
        save(owner, null).andExpect(status().isOk());
        var request = post(base() + "/resume");
        if (!tag.equals("absent")) request.header(HttpHeaders.IF_MATCH, tag);
        call(request, owner).andExpect(status().isPreconditionFailed());
    }

    @Test
    void missingSavedResultAndStaleSaveLeaveStateUnchanged() throws Exception {
        call(get(base() + "/saved-result"), owner).andExpect(status().isNotFound());
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), owner)
                .andExpect(status().isNotFound());
        save(owner, "\"ws_0-v0\"").andExpect(status().isPreconditionFailed());
        save(owner, "").andExpect(status().isPreconditionFailed());
        assertThat(savedResults.existsById(worksheetId)).isFalse();
        save(owner, "*").andExpect(status().isOk());
    }

    @Test
    void snapshotSchemaOrFileMismatchCannotOverwriteWorkingEdits() throws Exception {
        save(owner, null).andExpect(status().isOk());
        editCorrection("keep working");
        jdbc.update("update worksheet_saved_results set schema_version=999 where worksheet_id=?", worksheetId);
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), owner)
                .andExpect(status().isInternalServerError());
        assertThat(corrections.findById(correctionId).orElseThrow().getContent())
                .isEqualTo("keep working");
        jdbc.update("update worksheet_saved_results set schema_version=1 where worksheet_id=?", worksheetId);
        jdbc.update("update worksheets set file_hash='different' where id=?", worksheetId);
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), owner)
                .andExpect(status().isConflict());
        call(get(base() + "/saved-result"), owner).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"peer", "director"})
    void existingEditRoutesCannotBypassSavedWorkspaceOwnership(String who) throws Exception {
        save(owner, null).andExpect(status().isOk());
        User user = who.equals("peer") ? peer : director;
        call(patch(base() + "/corrections/" + correctionId + "/apply").param("applied", "false"), user)
                .andExpect(status().isForbidden());
        call(
                        patch(base() + "/corrections/" + correctionId)
                                .header(HttpHeaders.IF_MATCH, cardEtag())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"blocked\"}"),
                        user)
                .andExpect(status().isForbidden());
        call(delete(base() + "/corrections/" + correctionId).header(HttpHeaders.IF_MATCH, cardEtag()), user)
                .andExpect(status().isForbidden());
        call(post(base() + "/corrections/" + deletedCorrectionId + "/restore"), user)
                .andExpect(status().isForbidden());
        call(
                        patch(base() + "/comments/" + commentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"blocked\"}"),
                        user)
                .andExpect(status().isForbidden());
        call(delete(base() + "/comments/" + commentId), user).andExpect(status().isForbidden());
        call(
                        post(base() + "/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"pageId\":" + pageId
                                                + ",\"highlightType\":\"HIGHLIGHT\",\"content\":\"blocked\",\"regions\":[{\"x\":0.1,\"y\":0.1,\"width\":0.1,\"height\":0.1}]}"),
                        user)
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1", "v1.1"})
    void restoresVersionedAiEditsWithoutReplacingImmutableSource(String contract) throws Exception {
        tx.executeWithoutResult(s -> {
            Worksheet w = worksheets.findById(worksheetId).orElseThrow();
            var anchor = new CorrectionAnchor(1L, 1, 1, 0, 8);
            var edit = new AiEdit(AiEditOperation.REPLACE, "AI content", null);
            var details = new CorrectionV11Details(List.of(anchor), AiRevisionScope.WORD, edit, edit, List.of());
            var builder = Correction.builder()
                    .worksheet(w)
                    .page(pages.findById(pageId).orElseThrow())
                    .generation(generations
                            .findFirstByWorksheetIdOrderByCreatedAtDescIdDesc(worksheetId)
                            .orElseThrow())
                    .area(CorrectionArea.GRAMMAR_SPELLING)
                    .originalText("original")
                    .aiInitialContent("AI content")
                    .explanation("immutable explanation")
                    .regions(List.of())
                    .action(AiAction.REPLACE)
                    .feedbackType(FeedbackType.CORRECTION)
                    .issueKinds(List.of(AiIssueKind.GRAMMAR))
                    .anchor(anchor)
                    .locationUncertain(false)
                    .warnings(List.of())
                    .requiresReview(false);
            if (contract.equals("v1.1")) builder.v11Details(details);
            correctionId = corrections.saveAndFlush(builder.build()).getId();
        });
        save(owner, null).andExpect(status().isOk());
        editCorrection("new text");
        if (contract.equals("v1.1")) {
            assertThat(corrections
                            .findById(correctionId)
                            .orElseThrow()
                            .getV11Details()
                            .edit())
                    .isNull();
        }
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), owner)
                .andExpect(status().isOk());
        Correction restored = corrections.findById(correctionId).orElseThrow();
        assertThat(restored.getContent()).isEqualTo("AI content");
        assertThat(restored.getOriginalText()).isEqualTo("original");
        assertThat(restored.getAiInitialContent()).isEqualTo("AI content");
        assertThat(restored.getExplanation()).isEqualTo("immutable explanation");
        if (contract.equals("v1.1")) {
            assertThat(restored.getV11Details().edit().text()).isEqualTo("AI content");
            assertThat(restored.getV11Details().aiInitialEdit())
                    .isEqualTo(restored.getV11Details().edit());
        }
    }

    @Test
    void newerGenerationCannotUsePreviousSnapshot() throws Exception {
        save(owner, null).andExpect(status().isOk());
        tx.executeWithoutResult(s -> generations.saveAndFlush(WorksheetGeneration.builder()
                .worksheet(worksheets.findById(worksheetId).orElseThrow())
                .requestedBy(owner)
                .idempotencyKey(UUID.randomUUID().toString())
                .requestFileHash("b5-file")
                .requestSettingsSnapshot(Map.of())
                .build()));
        call(get(base() + "/saved-result"), owner).andExpect(status().isConflict());
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), owner)
                .andExpect(status().isConflict());
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedRestoreRollsBackAlreadyRestoredCorrections() throws Exception {
        save(owner, null).andExpect(status().isOk());
        editCorrection("must remain");
        String before = etag();
        tx.executeWithoutResult(s -> {
            var saved = savedResults.findById(worksheetId).orElseThrow();
            var data = new java.util.LinkedHashMap<>(saved.getSnapshot());
            var list = (List<Map<String, Object>>) data.get("comments");
            var badComment = new java.util.LinkedHashMap<>(list.getFirst());
            badComment.put("content", "");
            data.put("comments", List.of(badComment));
            saved.replace(saved.getSourceGeneration(), saved.getSavedBy(), saved.getSavedAt(), data);
        });
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, before), owner)
                .andExpect(status().isInternalServerError());
        assertThat(corrections.findById(correctionId).orElseThrow().getContent())
                .isEqualTo("must remain");
        assertThat(etag()).isEqualTo(before);
    }

    @Test
    void saveWaitsForEntireEditingTransaction() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var editing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var editor = pool.submit(() -> tx.executeWithoutResult(s -> {
                correctionService.updateCorrection(
                        owner.getId(),
                        owner.getAcademy().getId(),
                        worksheetId,
                        correctionId,
                        cardEtag(),
                        new CorrectionUpdateRequest("first edit"));
                editing.countDown();
                await(release);
                correctionService.updateCorrection(
                        owner.getId(),
                        owner.getAcademy().getId(),
                        worksheetId,
                        correctionId,
                        cardEtag(),
                        new CorrectionUpdateRequest("finished edit"));
            }));
            assertThat(editing.await(5, TimeUnit.SECONDS)).isTrue();
            var started = new CountDownLatch(1);
            var save = pool.submit(() -> {
                started.countDown();
                return saveService.saveDraft(owner.getId(), owner.getAcademy().getId(), worksheetId, null);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> save.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            editor.get(5, TimeUnit.SECONDS);
            assertThat(save.get(5, TimeUnit.SECONDS)
                            .review()
                            .corrections()
                            .getFirst()
                            .content())
                    .isEqualTo("finished edit");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void resumeChecksVersionAfterWaitingForAnotherEdit() throws Exception {
        save(owner, null).andExpect(status().isOk());
        String oldTag = etag();
        var pool = Executors.newFixedThreadPool(2);
        var editing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var editor = pool.submit(() -> tx.executeWithoutResult(s -> {
                correctionService.updateCorrection(
                        owner.getId(),
                        owner.getAcademy().getId(),
                        worksheetId,
                        correctionId,
                        cardEtag(),
                        new CorrectionUpdateRequest("concurrent edit"));
                editing.countDown();
                await(release);
            }));
            assertThat(editing.await(5, TimeUnit.SECONDS)).isTrue();
            var resume = pool.submit(() -> {
                assertThatThrownBy(() -> saveService.resume(
                                owner.getId(), owner.getAcademy().getId(), worksheetId, oldTag))
                        .isInstanceOfSatisfying(
                                CustomException.class,
                                e -> assertThat(e.getErrorCode().name()).isEqualTo("PRECONDITION_FAILED"));
            });
            assertThrows(TimeoutException.class, () -> resume.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            editor.get(5, TimeUnit.SECONDS);
            resume.get(5, TimeUnit.SECONDS);
            assertThat(corrections.findById(correctionId).orElseThrow().getContent())
                    .isEqualTo("concurrent edit");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private User user(Academy academy, Role role) {
        return users.saveAndFlush(User.builder()
                .academy(academy)
                .role(role)
                .name("B5 user")
                .email(UUID.randomUUID() + "@test.com")
                .passwordHash("hash")
                .build());
    }

    private Correction card(Worksheet w, WorksheetPage p, WorksheetGeneration g) {
        return Correction.builder()
                .worksheet(w)
                .page(p)
                .generation(g)
                .area(CorrectionArea.GRAMMAR_SPELLING)
                .originalText("original")
                .aiInitialContent("AI content")
                .explanation("explanation")
                .regions(List.of(new RegionPayload(p.getId(), 0.1, 0.1, 0.1, 0.1, null)))
                .build();
    }

    private Comment comment(Worksheet w, WorksheetPage p, String content) {
        return Comment.builder()
                .worksheet(w)
                .page(p)
                .user(owner)
                .highlightType(HighlightType.HIGHLIGHT)
                .content(content)
                .regions(List.of(new RegionPayload(p.getId(), 0.1, 0.1, 0.1, 0.1, null)))
                .build();
    }

    private String base() {
        return "/api/v1/worksheets/" + worksheetId;
    }

    private String etag() {
        return worksheets.findById(worksheetId).orElseThrow().getEtag();
    }

    private String cardEtag() {
        return corrections.findById(correctionId).orElseThrow().getEtag();
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

    private ResultActions save(User user, String tag) throws Exception {
        var request = post(base() + "/save-draft");
        if (tag != null) request.header(HttpHeaders.IF_MATCH, tag);
        return call(request, user);
    }

    private JsonNode result(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsByteArray())
                .get("result");
    }

    private ResultActions editCorrectionWithTag(String content, String tag) throws Exception {
        return call(
                patch(base() + "/corrections/" + correctionId)
                        .header(HttpHeaders.IF_MATCH, tag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("content", content))),
                owner);
    }

    private void editCorrection(String content) throws Exception {
        editCorrectionWithTag(content, cardEtag()).andExpect(status().isOk());
    }
}
