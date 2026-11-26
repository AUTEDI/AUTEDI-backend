package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetStatus;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WorksheetStudentModelTest {

    @Autowired
    AcademyRepository academies;

    @Autowired
    UserRepository users;

    @Autowired
    StudentRepository students;

    @Autowired
    WorksheetRepository worksheets;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    private Academy academy;
    private User uploader;
    private Student student;

    @BeforeEach
    void setUp() {
        academy = academies.saveAndFlush(Academy.builder().name("모델 테스트 기관").build());
        uploader = user("업로더");
        student = students.saveAndFlush(
                Student.builder().academy(academy).name("김학생").grade("초등 3").build());
    }

    @Test
    void studentLookupIsScopedByAcademyAndPersistsAuditAndVersion() {
        Academy other = academies.saveAndFlush(Academy.builder().name("다른 기관").build());
        Long studentId = student.getId();
        Long academyId = academy.getId();
        entityManager.clear();

        assertThat(students.findByIdAndAcademyId(studentId, other.getId())).isEmpty();
        Student found = students.findByIdAndAcademyId(studentId, academyId).orElseThrow();
        assertThat(found.getAcademy().getId()).isEqualTo(academyId);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getVersion()).isZero();
        assertThat(found.getClassName()).isNull();
    }

    @Test
    void studentNameIsNotAUniqueTechnicalIdentifier() {
        Student another = students.saveAndFlush(Student.builder()
                .academy(academy)
                .name(student.getName())
                .grade("초등 4")
                .className("오후반")
                .studentPhone("010-1111-2222")
                .guardianPhone("010-3333-4444")
                .build());
        entityManager.clear();

        assertThat(another.getId()).isNotEqualTo(student.getId());
        Student found =
                students.findByIdAndAcademyId(another.getId(), academy.getId()).orElseThrow();
        assertThat(found.getGrade()).isEqualTo("초등 4");
        assertThat(found.getClassName()).isEqualTo("오후반");
        assertThat(found.getStudentPhone()).isEqualTo("010-1111-2222");
        assertThat(found.getGuardianPhone()).isEqualTo("010-3333-4444");
    }

    @Test
    void existingUploadBuilderStillCreatesAnUnsavedWorksheetWithoutStudentOrTitle() {
        Worksheet worksheet = worksheet();
        Long id = worksheet.getId();
        entityManager.clear();

        Worksheet found = worksheets.findByIdAndAcademyId(id, academy.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(WorksheetStatus.DRAFT);
        assertThat(found.getSaveStatus()).isEqualTo(WorksheetSaveStatus.UNSAVED);
        assertThat(found.getTitle()).isNull();
        assertThat(found.getStudent()).isNull();
        assertThat(found.getFirstSavedAt()).isNull();
        assertThat(found.getLastSavedAt()).isNull();
        assertThat(found.getCompletedAt()).isNull();
        assertThat(found.getLastSavedBy()).isNull();
        assertThat(found.getDeletedAt()).isNull();
    }

    @Test
    void legacyInsertGetsDatabaseDefaultWithoutPromotingReadyToCompleted() {
        String hash = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO worksheets
                  (academy_id, user_id, status, original_filename, file_url, file_type,
                   file_size, file_hash, page_count, version, created_at, updated_at)
                VALUES (?, ?, 'READY', 'legacy.png', '/legacy.png', 'PNG', 100, ?, 1, 7,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                academy.getId(),
                uploader.getId(),
                hash);
        Long id = jdbc.queryForObject("SELECT id FROM worksheets WHERE file_hash = ?", Long.class, hash);
        entityManager.clear();

        Worksheet found = worksheets.findByIdAndAcademyId(id, academy.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(WorksheetStatus.READY);
        assertThat(found.getSaveStatus()).isEqualTo(WorksheetSaveStatus.UNSAVED);
        assertThat(found.getVersion()).isEqualTo(7);
        assertThat(found.getTitle()).isNull();
        assertThat(found.getStudent()).isNull();
    }

    @Test
    void metadataAcceptsSameTenantAndRejectsOtherTenantWithoutPartialMutation() {
        Worksheet worksheet = worksheet();
        worksheet.updateMetadata("  첫 첨삭  ", student);
        worksheets.flush();

        Academy other = academies.saveAndFlush(Academy.builder().name("다른 기관").build());
        Student outsider = students.saveAndFlush(
                Student.builder().academy(other).name("다른 학생").grade("초등 3").build());

        assertThatThrownBy(() -> worksheet.updateMetadata("바뀌면 안 되는 제목", outsider))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(worksheet.getTitle()).isEqualTo("첫 첨삭");
        assertThat(worksheet.getStudent().getId()).isEqualTo(student.getId());
        assertThat(worksheet.getUser().getId()).isEqualTo(uploader.getId());
        assertThat(worksheet.getSaveStatus()).isEqualTo(WorksheetSaveStatus.UNSAVED);
    }

    @ParameterizedTest
    @EnumSource(
            value = WorksheetStatus.class,
            names = {"PROCESSING", "READY", "FAILED"})
    void aiStatusChangesPreserveBusinessSaveMetadata(WorksheetStatus aiStatus) {
        Worksheet worksheet = worksheet();
        worksheet.updateMetadata("저장한 제목", student);
        worksheets.flush();
        LocalDateTime savedAt = LocalDateTime.of(2026, 11, 26, 12, 0);
        jdbc.update(
                "UPDATE worksheets SET save_status = 'TEMP_SAVED', first_saved_at = ?, last_saved_at = ? WHERE id = ?",
                savedAt,
                savedAt,
                worksheet.getId());
        entityManager.clear();

        Worksheet found = worksheets
                .findByIdAndAcademyId(worksheet.getId(), academy.getId())
                .orElseThrow();
        found.updateStatus(aiStatus);
        worksheets.flush();
        entityManager.clear();

        Worksheet restored = worksheets
                .findByIdAndAcademyId(worksheet.getId(), academy.getId())
                .orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(aiStatus);
        assertThat(restored.getSaveStatus()).isEqualTo(WorksheetSaveStatus.TEMP_SAVED);
        assertThat(restored.getTitle()).isEqualTo("저장한 제목");
        assertThat(restored.getStudent().getId()).isEqualTo(student.getId());
        assertThat(restored.getFirstSavedAt()).isEqualTo(savedAt);
        assertThat(restored.getLastSavedAt()).isEqualTo(savedAt);
        assertThat(restored.getCompletedAt()).isNull();
    }

    @Test
    void futureSavedAndDeletedValuesRoundTripWithoutChangingTheUploader() {
        Worksheet worksheet = worksheet();
        User saver = user("마지막 저장자");
        LocalDateTime first = LocalDateTime.of(2026, 11, 25, 10, 0);
        LocalDateTime last = first.plusDays(1);
        LocalDateTime deleted = last.plusHours(1);
        jdbc.update(
                """
                UPDATE worksheets SET save_status = 'COMPLETED', first_saved_at = ?, last_saved_at = ?,
                  completed_at = ?, last_saved_by = ?, deleted_at = ? WHERE id = ?
                """,
                first,
                last,
                last,
                saver.getId(),
                deleted,
                worksheet.getId());
        entityManager.clear();

        Worksheet found = worksheets
                .findByIdAndAcademyId(worksheet.getId(), academy.getId())
                .orElseThrow();
        assertThat(found.getSaveStatus()).isEqualTo(WorksheetSaveStatus.COMPLETED);
        assertThat(found.getFirstSavedAt()).isEqualTo(first);
        assertThat(found.getLastSavedAt()).isEqualTo(last);
        assertThat(found.getCompletedAt()).isEqualTo(last);
        assertThat(found.getDeletedAt()).isEqualTo(deleted);
        assertThat(found.getLastSavedBy().getId()).isEqualTo(saver.getId());
        assertThat(found.getUser().getId()).isEqualTo(uploader.getId());
        assertThat(students.findById(student.getId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "DELETED"})
    void metadataCannotChangeOnCompletedOrDeletedWorksheets(String state) {
        Worksheet worksheet = worksheet();
        worksheet.updateMetadata("기존 제목", student);
        worksheets.flush();
        if (state.equals("COMPLETED")) {
            jdbc.update("UPDATE worksheets SET save_status = 'COMPLETED' WHERE id = ?", worksheet.getId());
        } else {
            jdbc.update("UPDATE worksheets SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", worksheet.getId());
        }
        entityManager.clear();

        Worksheet found = worksheets
                .findByIdAndAcademyId(worksheet.getId(), academy.getId())
                .orElseThrow();
        assertThatThrownBy(() -> found.updateMetadata("변경 제목", student)).isInstanceOf(IllegalStateException.class);
        assertThat(found.getTitle()).isEqualTo("기존 제목");
    }

    private User user(String name) {
        return users.saveAndFlush(User.builder()
                .academy(academy)
                .email("model-" + UUID.randomUUID() + "@example.com")
                .passwordHash("not-used-for-login")
                .name(name)
                .build());
    }

    private Worksheet worksheet() {
        return worksheets.saveAndFlush(Worksheet.builder()
                .academy(academy)
                .user(uploader)
                .originalFilename("model.png")
                .fileUrl("/model.png")
                .fileType(FileType.PNG)
                .fileSize(100L)
                .fileHash(UUID.randomUUID().toString())
                .build());
    }
}
