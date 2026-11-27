package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.comment.entity.Comment;
import com.ceos24.autedi.domain.comment.entity.HighlightType;
import com.ceos24.autedi.domain.comment.repository.CommentRepository;
import com.ceos24.autedi.domain.correction.dto.request.CorrectionUpdateRequest;
import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.correction.repository.CorrectionRepository;
import com.ceos24.autedi.domain.correction.service.CorrectionService;
import com.ceos24.autedi.domain.generation.entity.GenerationStatus;
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
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSavedResult;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSavedResultRepository;
import com.ceos24.autedi.domain.worksheet.service.WorksheetSavedResultService;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 완료 상태·최종 저장본·기존 쓰기 차단의 원자성을 실제 HTTP와 커밋된 DB로 검증한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:b6_api;DB_CLOSE_DELAY=-1;MODE=MySQL")
class WorksheetCompletionApiTest {
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

    @MockitoSpyBean
    WorksheetSavedResultRepository savedResults;

    @Autowired
    WorksheetSavedResultService saveService;

    @Autowired
    WorksheetService worksheetService;

    @Autowired
    CorrectionService correctionService;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private TransactionTemplate tx;
    private MockMvc mvc;
    private User owner, peer, director, foreign;
    private Long worksheetId, pageId, generationId, correctionId, deletedCorrectionId, commentId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(manager);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        tx.executeWithoutResult(s -> {
            var academy = academies.saveAndFlush(Academy.builder().name("B6").build());
            owner = user(academy, Role.TEACHER);
            peer = user(academy, Role.TEACHER);
            director = user(academy, Role.DIRECTOR);
            foreign = user(
                    academies.saveAndFlush(Academy.builder().name("foreign").build()), Role.TEACHER);
            var student = students.saveAndFlush(
                    Student.builder().academy(academy).name("김학생").grade("초3").build());
            var worksheet = worksheets.saveAndFlush(Worksheet.builder()
                    .academy(academy)
                    .user(owner)
                    .originalFilename("b6.png")
                    .fileUrl("/b6.png")
                    .fileType(FileType.PNG)
                    .fileSize(100L)
                    .fileHash("b6-file")
                    .build());
            worksheet.updateMetadata("최종 작문", student);
            worksheet.updateStatus(WorksheetStatus.READY);
            var page = pages.saveAndFlush(WorksheetPage.builder()
                    .worksheet(worksheet)
                    .pageNumber(1)
                    .previewImageUrl("/b6-preview.png")
                    .width(1000)
                    .height(1400)
                    .build());
            var generation = generations.saveAndFlush(WorksheetGeneration.builder()
                    .worksheet(worksheet)
                    .requestedBy(owner)
                    .idempotencyKey(UUID.randomUUID().toString())
                    .requestFileHash("b6-file")
                    .requestSettingsSnapshot(Map.of())
                    .build());
            generation.succeed();
            worksheetId = worksheet.getId();
            pageId = page.getId();
            generationId = generation.getId();
            correctionId =
                    corrections.saveAndFlush(card(worksheet, page, generation)).getId();
            var removed = corrections.saveAndFlush(card(worksheet, page, generation));
            removed.delete();
            deletedCorrectionId = removed.getId();
            commentId = comments.saveAndFlush(Comment.builder()
                            .worksheet(worksheet)
                            .page(page)
                            .user(owner)
                            .highlightType(HighlightType.HIGHLIGHT)
                            .regions(List.of(region()))
                            .content("final comment")
                            .build())
                    .getId();
        });
    }

    @Test
    void directCompletionCreatesSnapshotAndCanBeReadWithoutDraftSave() throws Exception {
        assertThat(savedResults.existsById(worksheetId)).isFalse();
        String before = etag();
        var action = complete(owner, before).andExpect(status().isOk());
        JsonNode result = result(action);
        assertThat(result.path("revision").asLong()).isEqualTo(1);
        assertThat(action.andReturn().getResponse().getHeader(HttpHeaders.ETAG))
                .isEqualTo(result.path("etag").asText());
        assertThat(result.at("/review/worksheet/etag").asText()).isNotEqualTo(before);
        assertThat(result.at("/review/worksheet/status").asText()).isEqualTo("READY");
        assertThat(result.at("/review/worksheet/saveStatus").asText()).isEqualTo("COMPLETED");
        String completedAt = result.at("/review/worksheet/completedAt").asText();
        assertThat(result.at("/review/worksheet/firstSavedAt").asText()).isEqualTo(completedAt);
        assertThat(result.at("/review/worksheet/lastSavedAt").asText()).isEqualTo(completedAt);
        assertThat(result.path("savedAt").asText()).isEqualTo(completedAt);
        assertThat(result.path("savedBy").asLong()).isEqualTo(owner.getId());
        assertThat(result.at("/review/corrections/0/content").asText()).isEqualTo("AI content");
        assertThat(result.at("/review/comments/0/content").asText()).isEqualTo("final comment");
        assertThat(result(call(get(base() + "/saved-result"), owner).andExpect(status().isOk())))
                .isEqualTo(result);
        call(get(base() + "/review"), owner).andExpect(status().isOk());
        tx.executeWithoutResult(s -> {
            var worksheet = worksheets.findById(worksheetId).orElseThrow();
            assertThat(worksheet.getUser().getId()).isEqualTo(owner.getId());
            assertThat(worksheet.getLastSavedBy().getId()).isEqualTo(owner.getId());
            assertThat(worksheet.getCompletedAt()).isNotNull();
        });
    }

    @Test
    void completionCapturesUnsavedEditsAndReplacesDraftSnapshot() throws Exception {
        JsonNode draft = result(call(post(base() + "/save-draft"), owner).andExpect(status().isOk()));
        edit("final edited content");
        JsonNode completed = result(complete(owner, etag()).andExpect(status().isOk()));
        assertThat(completed.path("revision").asLong()).isEqualTo(2);
        assertThat(completed.at("/review/worksheet/firstSavedAt"))
                .isEqualTo(draft.at("/review/worksheet/firstSavedAt"));
        assertThat(completed.at("/review/corrections/0/content").asText()).isEqualTo("final edited content");
        assertThat(completed.at("/review/worksheet/completedAt").asText())
                .isEqualTo(completed.path("savedAt").asText());
        assertThat(savedResults.findAll().stream()
                        .filter(r -> r.getWorksheetId().equals(worksheetId)))
                .hasSize(1);
    }

    @Test
    void repeatedCompletionPreservesSnapshotTimestampsAndVersions() throws Exception {
        String originalTag = etag();
        JsonNode first = result(complete(owner, originalTag).andExpect(status().isOk()));
        String currentTag = etag();
        var row = savedResults.findById(worksheetId).orElseThrow();
        complete(owner, originalTag).andExpect(status().isPreconditionFailed());
        assertThat(result(complete(owner, currentTag).andExpect(status().isOk())))
                .isEqualTo(first);
        assertThat(etag()).isEqualTo(currentTag);
        var repeated = savedResults.findById(worksheetId).orElseThrow();
        assertThat(repeated.getVersion()).isEqualTo(row.getVersion());
        assertThat(repeated.getUpdatedAt()).isEqualTo(row.getUpdatedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"peer", "director", "foreign", "anonymous"})
    void completionIsOwnerOnly(String who) throws Exception {
        User user = who.equals("peer") ? peer : who.equals("director") ? director : foreign;
        if (who.equals("anonymous"))
            mvc.perform(post(base() + "/complete").header(HttpHeaders.IF_MATCH, etag()))
                    .andExpect(status().isUnauthorized());
        else complete(user, etag()).andExpect(status().is(who.equals("foreign") ? 404 : 403));
        assertUnchanged();
    }

    @ParameterizedTest
    @EnumSource(
            value = WorksheetStatus.class,
            names = {"DRAFT", "PROCESSING", "FAILED"})
    void onlyReadyCanComplete(WorksheetStatus state) throws Exception {
        jdbc.update("update worksheets set status=? where id=?", state.name(), worksheetId);
        complete(owner, etag()).andExpect(status().isConflict());
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "*", "", "\"ws_0-v0\"", "W/\"ws_1-v1\"", "\"saved_ws_1-r1\""})
    void completionRequiresExactCurrentWorksheetEtag(String tag) throws Exception {
        complete(owner, tag.equals("absent") ? null : tag).andExpect(status().isPreconditionFailed());
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "title",
                "student",
                "foreignStudent",
                "generation",
                "hash",
                "pages",
                "pageCount",
                "pageSize",
                "preview"
            })
    void missingOrInconsistentRequiredDataCannotComplete(String target) throws Exception {
        switch (target) {
            case "title" -> jdbc.update("update worksheets set title=null where id=?", worksheetId);
            case "student" -> jdbc.update("update worksheets set student_id=null where id=?", worksheetId);
            case "foreignStudent" -> {
                Long id = tx.execute(s -> students.saveAndFlush(Student.builder()
                                .academy(foreign.getAcademy())
                                .name("other")
                                .grade("초4")
                                .build())
                        .getId());
                jdbc.update("update worksheets set student_id=? where id=?", id, worksheetId);
            }
            case "generation" -> jdbc.update(
                    "update worksheet_generations set worksheet_id=? where id=?", otherWorksheetId(), generationId);
            case "hash" -> jdbc.update(
                    "update worksheet_generations set request_file_hash='old-file' where id=?", generationId);
            case "pages" -> jdbc.update(
                    "update worksheet_pages set worksheet_id=? where id=?", otherWorksheetId(), pageId);
            case "pageCount" -> jdbc.update("update worksheets set page_count=2 where id=?", worksheetId);
            case "pageSize" -> jdbc.update("update worksheet_pages set width=0 where id=?", pageId);
            case "preview" -> jdbc.update("update worksheet_pages set preview_image_url='' where id=?", pageId);
            default -> throw new AssertionError(target);
        }
        complete(owner, etag()).andExpect(status().isConflict());
        assertUnchanged();
    }

    @ParameterizedTest
    @EnumSource(
            value = GenerationStatus.class,
            names = {"PENDING", "RUNNING", "FAILED"})
    void latestGenerationMustHaveSucceeded(GenerationStatus state) throws Exception {
        jdbc.update("update worksheet_generations set status=? where id=?", state.name(), generationId);
        complete(owner, etag()).andExpect(status().isConflict());
        assertUnchanged();
    }

    @Test
    void latestFailedGenerationIsNotReplacedByOlderSuccess() throws Exception {
        tx.executeWithoutResult(s -> {
            var latest = generations.saveAndFlush(WorksheetGeneration.builder()
                    .worksheet(worksheets.findById(worksheetId).orElseThrow())
                    .requestedBy(owner)
                    .idempotencyKey(UUID.randomUUID().toString())
                    .requestFileHash("b6-file")
                    .requestSettingsSnapshot(Map.of())
                    .build());
            latest.fail("test", "failed", true);
        });
        complete(owner, etag()).andExpect(status().isConflict());
        assertUnchanged();
    }

    @Test
    void zeroCorrectionsAndCommentsAreValidSuccessfulResults() throws Exception {
        jdbc.update("delete from comments where worksheet_id=?", worksheetId);
        jdbc.update("delete from corrections where worksheet_id=?", worksheetId);
        JsonNode completed = result(complete(owner, etag()).andExpect(status().isOk()));
        assertThat(completed.at("/review/corrections").size()).isZero();
        assertThat(completed.at("/review/comments").size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"completed", "deleted"})
    void everyExistingContentWriteRejectsClosedWorksheet(String state) throws Exception {
        if (state.equals("completed")) complete(owner, etag()).andExpect(status().isOk());
        else jdbc.update("update worksheets set deleted_at=current_timestamp where id=?", worksheetId);
        int expected = state.equals("completed") ? 409 : 404;
        call(post(base() + "/save-draft"), owner).andExpect(status().is(expected));
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), owner)
                .andExpect(status().is(expected));
        call(
                        patch(base() + "/corrections/" + correctionId)
                                .header(HttpHeaders.IF_MATCH, cardEtag())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"blocked\"}"),
                        owner)
                .andExpect(status().is(expected));
        call(patch(base() + "/corrections/" + correctionId + "/apply").param("applied", "false"), owner)
                .andExpect(status().is(expected));
        call(delete(base() + "/corrections/" + correctionId).header(HttpHeaders.IF_MATCH, cardEtag()), owner)
                .andExpect(status().is(expected));
        call(post(base() + "/corrections/" + deletedCorrectionId + "/restore"), owner)
                .andExpect(status().is(expected));
        call(
                        post(base() + "/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"pageId\":" + pageId
                                                + ",\"highlightType\":\"HIGHLIGHT\",\"content\":\"blocked\",\"regions\":[{\"x\":0.1,\"y\":0.1,\"width\":0.1,\"height\":0.1}]}"),
                        owner)
                .andExpect(status().is(expected));
        call(
                        patch(base() + "/comments/" + commentId)
                                .header(
                                        HttpHeaders.IF_MATCH,
                                        comments.findById(commentId)
                                                .orElseThrow()
                                                .getEtag())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"blocked\"}"),
                        owner)
                .andExpect(status().is(expected));
        call(delete(base() + "/comments/" + commentId), owner).andExpect(status().is(expected));
        call(
                        patch(base())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"blocked\",\"studentId\":1}"),
                        owner)
                .andExpect(status().is(expected));
        call(
                        put(base() + "/settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"areas\":[{\"area\":\"GRAMMAR_SPELLING\",\"level\":\"STANDARD\"}]}"),
                        owner)
                .andExpect(status().is(expected));
        call(
                        multipart(HttpMethod.PUT, base() + "/file")
                                .file(new MockMultipartFile("file", "x.png", "image/png", new byte[] {1})),
                        owner)
                .andExpect(status().is(expected));
        call(post(base() + "/generations"), owner).andExpect(status().is(expected));
        call(post(base() + "/generations/" + generationId + "/retry"), owner).andExpect(status().is(expected));
    }

    @Test
    void completedSnapshotIsSharedWithinAcademyAndDeletedResultsAreHidden() throws Exception {
        complete(owner, etag()).andExpect(status().isOk());
        call(get(base() + "/saved-result"), peer).andExpect(status().isOk());
        call(get(base() + "/saved-result"), director).andExpect(status().isOk());
        complete(peer, etag()).andExpect(status().isForbidden());
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), peer)
                .andExpect(status().isConflict());
        call(get(base() + "/saved-result"), foreign).andExpect(status().isNotFound());
        jdbc.update("update worksheets set deleted_at=current_timestamp where id=?", worksheetId);
        call(get(base() + "/saved-result"), owner).andExpect(status().isNotFound());
        complete(owner, etag()).andExpect(status().isNotFound());
    }

    @Test
    void failedSnapshotWriteRollsBackCompletionAndPreviousDraft() throws Exception {
        JsonNode draft = result(call(post(base() + "/save-draft"), owner).andExpect(status().isOk()));
        String before = etag();
        doThrow(new DataIntegrityViolationException("forced snapshot failure"))
                .when(savedResults)
                .saveAndFlush(any(WorksheetSavedResult.class));
        complete(owner, before).andExpect(status().is5xxServerError());
        assertThat(etag()).isEqualTo(before);
        assertThat(worksheets.findById(worksheetId).orElseThrow().getSaveStatus())
                .isEqualTo(WorksheetSaveStatus.TEMP_SAVED);
        assertThat(worksheets.findById(worksheetId).orElseThrow().getCompletedAt())
                .isNull();
        assertThat(result(call(get(base() + "/saved-result"), owner).andExpect(status().isOk())))
                .isEqualTo(draft);
    }

    @Test
    void editWaitingForCompletionIsRejectedAfterCommit() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var completed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        String tag = etag(), correctionTag = cardEtag();
        try {
            var completing = pool.submit(() -> tx.executeWithoutResult(s -> {
                saveService.complete(owner.getId(), owner.getAcademy().getId(), worksheetId, tag);
                completed.countDown();
                await(release);
            }));
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            var editing = pool.submit(() -> assertThatThrownBy(() -> correctionService.updateCorrection(
                            owner.getId(),
                            owner.getAcademy().getId(),
                            worksheetId,
                            correctionId,
                            correctionTag,
                            new CorrectionUpdateRequest("too late")))
                    .isInstanceOfSatisfying(
                            CustomException.class,
                            e -> assertThat(e.getErrorCode().name()).isEqualTo("WORKSHEET_COMPLETED")));
            assertThrows(TimeoutException.class, () -> editing.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            completing.get(5, TimeUnit.SECONDS);
            editing.get(5, TimeUnit.SECONDS);
            assertThat(corrections.findById(correctionId).orElseThrow().getContent())
                    .isEqualTo("AI content");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void completionWaitingForEditRejectsStaleVersion() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var edited = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        String tag = etag();
        try {
            var editing = pool.submit(() -> tx.executeWithoutResult(s -> {
                correctionService.updateCorrection(
                        owner.getId(),
                        owner.getAcademy().getId(),
                        worksheetId,
                        correctionId,
                        cardEtag(),
                        new CorrectionUpdateRequest("newer edit"));
                edited.countDown();
                await(release);
            }));
            assertThat(edited.await(5, TimeUnit.SECONDS)).isTrue();
            var completing = pool.submit(() -> assertThatThrownBy(() -> saveService.complete(
                            owner.getId(), owner.getAcademy().getId(), worksheetId, tag))
                    .isInstanceOfSatisfying(
                            CustomException.class,
                            e -> assertThat(e.getErrorCode().name()).isEqualTo("PRECONDITION_FAILED")));
            assertThrows(TimeoutException.class, () -> completing.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            editing.get(5, TimeUnit.SECONDS);
            completing.get(5, TimeUnit.SECONDS);
            assertUnchanged();
            JsonNode result = result(complete(owner, etag()).andExpect(status().isOk()));
            assertThat(result.at("/review/corrections/0/content").asText()).isEqualTo("newer edit");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void simultaneousCompletionsCreateOnlyOneVersion() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        String tag = etag();
        try {
            java.util.concurrent.Callable<String> task = () -> {
                await(start);
                try {
                    saveService.complete(owner.getId(), owner.getAcademy().getId(), worksheetId, tag);
                    return "OK";
                } catch (CustomException e) {
                    return e.getErrorCode().name();
                }
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "PRECONDITION_FAILED");
            assertThat(savedResults.findById(worksheetId).orElseThrow().getRevision())
                    .isEqualTo(1);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void deletedDraftKeepsSnapshotAndEditsButHidesAllSaveRoutes() throws Exception {
        JsonNode draft = result(call(post(base() + "/save-draft"), owner).andExpect(status().isOk()));
        call(delete(base()), owner).andExpect(status().isNoContent());
        call(get(base() + "/saved-result"), owner).andExpect(status().isNotFound());
        call(get(base() + "/review"), owner).andExpect(status().isNotFound());
        call(post(base() + "/save-draft"), owner).andExpect(status().isNotFound());
        call(post(base() + "/resume").header(HttpHeaders.IF_MATCH, etag()), owner)
                .andExpect(status().isNotFound());
        complete(owner, etag()).andExpect(status().isNotFound());
        call(delete(base()), owner).andExpect(status().isNotFound());
        assertThat(worksheets.findById(worksheetId).orElseThrow().isDeleted()).isTrue();
        assertThat(savedResults.findById(worksheetId).orElseThrow().getRevision())
                .isEqualTo(draft.path("revision").asLong());
        assertThat(corrections.findById(correctionId).orElseThrow().getContent())
                .isEqualTo("AI content");
        assertThat(comments.findById(commentId).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void completionWaitingForDeletionCannotReviveWorksheet() throws Exception {
        call(post(base() + "/save-draft"), owner).andExpect(status().isOk());
        String tag = etag();
        var pool = Executors.newFixedThreadPool(2);
        var deleted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var deleting = pool.submit(() -> tx.executeWithoutResult(s -> {
                worksheetService.deleteWorksheet(
                        owner.getId(), owner.getAcademy().getId(), worksheetId);
                deleted.countDown();
                await(release);
            }));
            assertThat(deleted.await(5, TimeUnit.SECONDS)).isTrue();
            var completing = pool.submit(() -> assertThatThrownBy(() -> saveService.complete(
                            owner.getId(), owner.getAcademy().getId(), worksheetId, tag))
                    .isInstanceOfSatisfying(
                            CustomException.class,
                            e -> assertThat(e.getErrorCode().name()).isEqualTo("WORKSHEET_NOT_FOUND")));
            assertThrows(TimeoutException.class, () -> completing.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            deleting.get(5, TimeUnit.SECONDS);
            completing.get(5, TimeUnit.SECONDS);
            var worksheet = worksheets.findById(worksheetId).orElseThrow();
            assertThat(worksheet.isDeleted()).isTrue();
            assertThat(worksheet.getSaveStatus()).isEqualTo(WorksheetSaveStatus.TEMP_SAVED);
            assertThat(worksheet.getCompletedAt()).isNull();
            assertThat(savedResults.findById(worksheetId).orElseThrow().getRevision())
                    .isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void deletionWaitingForCompletionCannotRemoveFinalResult() throws Exception {
        call(post(base() + "/save-draft"), owner).andExpect(status().isOk());
        String tag = etag();
        var pool = Executors.newFixedThreadPool(2);
        var completed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var completing = pool.submit(() -> tx.executeWithoutResult(s -> {
                saveService.complete(owner.getId(), owner.getAcademy().getId(), worksheetId, tag);
                completed.countDown();
                await(release);
            }));
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            var deleting = pool.submit(() -> assertThatThrownBy(() -> worksheetService.deleteWorksheet(
                            owner.getId(), owner.getAcademy().getId(), worksheetId))
                    .isInstanceOfSatisfying(
                            CustomException.class,
                            e -> assertThat(e.getErrorCode().name()).isEqualTo("WORKSHEET_COMPLETED")));
            assertThrows(TimeoutException.class, () -> deleting.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            completing.get(5, TimeUnit.SECONDS);
            deleting.get(5, TimeUnit.SECONDS);
            var worksheet = worksheets.findById(worksheetId).orElseThrow();
            assertThat(worksheet.isDeleted()).isFalse();
            assertThat(worksheet.getSaveStatus()).isEqualTo(WorksheetSaveStatus.COMPLETED);
            assertThat(savedResults.findById(worksheetId).orElseThrow().getRevision())
                    .isEqualTo(2);
            call(get(base() + "/saved-result"), owner).andExpect(status().isOk());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private void assertUnchanged() {
        assertThat(savedResults.existsById(worksheetId)).isFalse();
        var worksheet = worksheets.findById(worksheetId).orElseThrow();
        assertThat(worksheet.getSaveStatus()).isEqualTo(WorksheetSaveStatus.UNSAVED);
        assertThat(worksheet.getCompletedAt()).isNull();
        assertThat(worksheet.getFirstSavedAt()).isNull();
    }

    private Long otherWorksheetId() {
        return tx.execute(s -> worksheets
                .saveAndFlush(Worksheet.builder()
                        .academy(foreign.getAcademy())
                        .user(foreign)
                        .originalFilename("other.png")
                        .fileUrl("/other.png")
                        .fileType(FileType.PNG)
                        .fileSize(1L)
                        .fileHash("other")
                        .build())
                .getId());
    }

    private User user(Academy academy, Role role) {
        return users.saveAndFlush(User.builder()
                .academy(academy)
                .role(role)
                .name("B6 user")
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
                .regions(List.of(region()))
                .build();
    }

    private RegionPayload region() {
        return new RegionPayload(pageId, 0.1, 0.1, 0.1, 0.1, null);
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

    private ResultActions call(AbstractMockHttpServletRequestBuilder<?> request, User user) throws Exception {
        return mvc.perform(request.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer "
                        + tokens.createAccessToken(
                                user.getId(),
                                user.getAcademy().getId(),
                                user.getEmail(),
                                user.getRole().name())));
    }

    private ResultActions complete(User user, String tag) throws Exception {
        var request = post(base() + "/complete");
        if (tag != null) request.header(HttpHeaders.IF_MATCH, tag);
        return call(request, user);
    }

    private JsonNode result(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsByteArray())
                .get("result");
    }

    private void edit(String content) throws Exception {
        call(
                        patch(base() + "/corrections/" + correctionId)
                                .header(HttpHeaders.IF_MATCH, cardEtag())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("content", content))),
                        owner)
                .andExpect(status().isOk());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
