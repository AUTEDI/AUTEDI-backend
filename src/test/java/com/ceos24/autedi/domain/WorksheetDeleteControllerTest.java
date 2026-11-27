package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class WorksheetDeleteControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AcademyRepository academies;

    @Autowired
    private UserRepository users;

    @Autowired
    private WorksheetRepository worksheets;

    @Autowired
    private WorksheetPageRepository pages;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;
    private Academy academy;
    private User teacher;
    private User peer;
    private User director;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academies.saveAndFlush(Academy.builder().name("삭제 기관").build());
        teacher = user(academy, "teacher", Role.TEACHER);
        peer = user(academy, "peer", Role.TEACHER);
        director = user(academy, "director", Role.DIRECTOR);
    }

    @Test
    @DisplayName("업로더 선생님은 임시저장 학습지를 삭제하고, 삭제 후에는 조회되지 않으며 데이터는 보존된다")
    void teacherDeletesOwnTempSavedWorksheet() throws Exception {
        Worksheet worksheet = worksheet(teacher, "TEMP_SAVED");

        mvc.perform(delete("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher)))
                .andExpect(status().isNoContent());

        em.flush();
        em.clear();
        Worksheet saved = worksheets.findById(worksheet.getId()).orElseThrow();
        assertThat(saved.isDeleted()).isTrue();
        assertThat(saved.getDeletedAt()).isNotNull();
        assertThat(pages.findAllByWorksheetIdOrderByPageNumberAsc(worksheet.getId()))
                .hasSize(1);

        mvc.perform(get("/api/v1/worksheets/{id}", worksheet.getId()).header(HttpHeaders.AUTHORIZATION, token(teacher)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"));
    }

    @Test
    @DisplayName("업로더가 원장이어도 본인 임시저장 학습지는 삭제할 수 있다")
    void directorDeletesOwnTempSavedWorksheet() throws Exception {
        Worksheet worksheet = worksheet(director, "TEMP_SAVED");

        mvc.perform(delete("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(director)))
                .andExpect(status().isNoContent());

        assertDeleted(worksheet, true);
    }

    @Test
    @DisplayName("같은 기관 원장이라도 남의 임시저장 학습지는 삭제할 수 없다")
    void directorCannotDeleteOthersWorksheet() throws Exception {
        Worksheet worksheet = worksheet(teacher, "TEMP_SAVED");

        mvc.perform(delete("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(director)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        assertDeleted(worksheet, false);
    }

    @Test
    @DisplayName("같은 기관의 다른 선생님은 삭제할 수 없다")
    void otherTeacherCannotDelete() throws Exception {
        Worksheet worksheet = worksheet(teacher, "TEMP_SAVED");

        mvc.perform(delete("/api/v1/worksheets/{id}", worksheet.getId()).header(HttpHeaders.AUTHORIZATION, token(peer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        assertDeleted(worksheet, false);
    }

    @Test
    @DisplayName("저장하지 않은(UNSAVED) 학습지는 삭제할 수 없다")
    void unsavedWorksheetCannotBeDeleted() throws Exception {
        Worksheet worksheet = worksheet(teacher, "UNSAVED");

        mvc.perform(delete("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSHEET_002"));

        assertDeleted(worksheet, false);
    }

    @Test
    @DisplayName("완료된 학습지는 삭제할 수 없고 완료 에러 코드를 받는다")
    void completedWorksheetCannotBeDeleted() throws Exception {
        Worksheet worksheet = worksheet(teacher, "COMPLETED");

        mvc.perform(delete("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSHEET_003"));

        assertDeleted(worksheet, false);
    }

    @Test
    @DisplayName("이미 삭제된 학습지를 다시 삭제하면 404이다")
    void deletingTwiceReturnsNotFound() throws Exception {
        Worksheet worksheet = worksheet(teacher, "TEMP_SAVED");
        String authorization = token(teacher);

        mvc.perform(delete("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"));
    }

    @Test
    @DisplayName("다른 기관 학습지와 없는 학습지는 404로 숨긴다")
    void otherAcademyAndMissingWorksheetAreHidden() throws Exception {
        Worksheet worksheet = worksheet(teacher, "TEMP_SAVED");
        Academy otherAcademy =
                academies.saveAndFlush(Academy.builder().name("다른 기관").build());
        User outsider = user(otherAcademy, "outsider", Role.DIRECTOR);

        mvc.perform(delete("/api/v1/worksheets/{id}", worksheet.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"));
        mvc.perform(delete("/api/v1/worksheets/{id}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, token(teacher)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHEET_001"));

        assertDeleted(worksheet, false);
    }

    @Test
    @DisplayName("인증 없이는 삭제할 수 없다")
    void requiresAuthentication() throws Exception {
        Worksheet worksheet = worksheet(teacher, "TEMP_SAVED");

        mvc.perform(delete("/api/v1/worksheets/{id}", worksheet.getId())).andExpect(status().isUnauthorized());

        assertDeleted(worksheet, false);
    }

    private void assertDeleted(Worksheet worksheet, boolean expected) {
        em.flush();
        em.clear();
        assertThat(worksheets.findById(worksheet.getId()).orElseThrow().isDeleted())
                .isEqualTo(expected);
    }

    private Worksheet worksheet(User owner, String saveStatus) {
        Worksheet worksheet = worksheets.saveAndFlush(Worksheet.builder()
                .academy(owner.getAcademy())
                .user(owner)
                .originalFilename("delete.png")
                .fileUrl("/delete.png")
                .fileType(FileType.PNG)
                .fileSize(100L)
                .fileHash("delete-file-hash")
                .build());
        pages.saveAndFlush(WorksheetPage.builder()
                .worksheet(worksheet)
                .pageNumber(1)
                .previewImageUrl("/delete-prepared.png")
                .width(1000)
                .height(1400)
                .build());
        em.flush();
        jdbc.update("UPDATE worksheets SET save_status = ? WHERE id = ?", saveStatus, worksheet.getId());
        em.clear();
        return worksheet;
    }

    private User user(Academy owner, String suffix, Role role) {
        return users.saveAndFlush(User.builder()
                .academy(owner)
                .email("delete_" + suffix + "@test.com")
                .passwordHash("hashed")
                .name(suffix)
                .role(role)
                .build());
    }

    private String token(User user) {
        return "Bearer "
                + tokens.createAccessToken(
                        user.getId(),
                        user.getAcademy().getId(),
                        user.getEmail(),
                        user.getRole().name());
    }
}
