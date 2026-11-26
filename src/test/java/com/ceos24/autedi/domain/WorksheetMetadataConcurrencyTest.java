package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.FileType;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class WorksheetMetadataConcurrencyTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AcademyRepository academies;

    @Autowired
    private UserRepository users;

    @Autowired
    private StudentRepository students;

    @Autowired
    private WorksheetRepository worksheets;

    @Autowired
    private JwtTokenProvider tokens;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    @Timeout(40)
    @DisplayName("같은 ETag의 동시 제목·학생 변경은 한 요청만 성공하고 다른 요청은 412다")
    void matchingEtagDoesNotAllowConcurrentOverwrite() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        Academy academy =
                academies.saveAndFlush(Academy.builder().name("B4 동시 수정 기관").build());
        User owner = users.saveAndFlush(User.builder()
                .academy(academy)
                .email(UUID.randomUUID() + "@b4-concurrency.test")
                .passwordHash("hashed")
                .name("업로더")
                .role(Role.TEACHER)
                .build());
        Student firstStudent = students.saveAndFlush(
                Student.builder().academy(academy).name("첫 학생").grade("초등 3").build());
        Student secondStudent = students.saveAndFlush(
                Student.builder().academy(academy).name("다른 학생").grade("초등 4").build());
        Worksheet worksheet = worksheets.saveAndFlush(Worksheet.builder()
                .academy(academy)
                .user(owner)
                .originalFilename("b4.png")
                .fileUrl("/b4.png")
                .fileType(FileType.PNG)
                .fileSize(100L)
                .fileHash("b4-race")
                .build());
        String etag = worksheet.getEtag();
        String token = "Bearer "
                + tokens.createAccessToken(owner.getId(), academy.getId(), owner.getEmail(), Role.TEACHER.name());
        var callers = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = callers.submit(
                    () -> update(mvc, token, worksheet.getId(), etag, "첫 제목", firstStudent.getId(), ready, start));
            var second = callers.submit(
                    () -> update(mvc, token, worksheet.getId(), etag, "다른 제목", secondStudent.getId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            MvcResult firstResult = first.get(15, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(15, TimeUnit.SECONDS);
            assertThat(new int[] {
                        firstResult.getResponse().getStatus(),
                        secondResult.getResponse().getStatus()
                    })
                    .containsExactlyInAnyOrder(200, 412);
            MvcResult success = firstResult.getResponse().getStatus() == 200 ? firstResult : secondResult;
            var response =
                    json.readTree(success.getResponse().getContentAsByteArray()).get("result");
            Worksheet saved = worksheets.findById(worksheet.getId()).orElseThrow();
            assertThat(saved.getTitle()).isEqualTo(response.get("title").asText());
            assertThat(saved.getStudent().getId())
                    .isEqualTo(response.at("/student/studentId").asLong());
            assertThat(saved.getVersion()).isEqualTo(2);
            assertThat(success.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo(saved.getEtag());
            MvcResult rejected = success == firstResult ? secondResult : firstResult;
            assertThat(json.readTree(rejected.getResponse().getContentAsByteArray())
                            .get("code")
                            .asText())
                    .isEqualTo("COMMON_412");
        } finally {
            start.countDown();
            callers.shutdownNow();
            assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            worksheets.deleteById(worksheet.getId());
            students.deleteById(firstStudent.getId());
            students.deleteById(secondStudent.getId());
            users.deleteById(owner.getId());
            academies.deleteById(academy.getId());
        }
    }

    private MvcResult update(
            MockMvc mvc,
            String token,
            Long worksheetId,
            String etag,
            String title,
            Long studentId,
            CountDownLatch ready,
            CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
        return mvc.perform(patch("/api/v1/worksheets/{id}", worksheetId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", title, "studentId", studentId))))
                .andReturn();
    }
}
