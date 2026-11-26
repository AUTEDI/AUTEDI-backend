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
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
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
class StudentUpdateConcurrencyTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AcademyRepository academies;

    @Autowired
    private StudentRepository students;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtTokenProvider tokens;

    @Test
    @Timeout(40)
    @DisplayName("동시에 서로 다른 기본 정보를 PATCH해도 생략한 필드의 최신 값이 보존된다")
    void concurrentPartialUpdatesRetainBothChanges() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        Academy academy =
                academies.saveAndFlush(Academy.builder().name("학생 동시 수정 기관").build());
        User teacher = users.saveAndFlush(User.builder()
                .academy(academy)
                .email(UUID.randomUUID() + "@student-update.test")
                .passwordHash("hashed")
                .name("김선생")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());
        Student student = students.saveAndFlush(Student.builder()
                .academy(academy)
                .name("김학생")
                .grade("초등 3")
                .className("오후반")
                .studentPhone("01012345678")
                .guardianPhone("01087654321")
                .build());
        String token = "Bearer "
                + tokens.createAccessToken(teacher.getId(), academy.getId(), teacher.getEmail(), Role.TEACHER.name());
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var grade = callers.submit(() -> update(mvc, token, student.getId(), "{\"grade\":\"초등 4\"}", ready, start));
            var phone = callers.submit(
                    () -> update(mvc, token, student.getId(), "{\"guardianPhone\":\"02-123-4567\"}", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(grade.get(15, TimeUnit.SECONDS).getResponse().getStatus())
                    .isEqualTo(200);
            assertThat(phone.get(15, TimeUnit.SECONDS).getResponse().getStatus())
                    .isEqualTo(200);

            Student saved = students.findById(student.getId()).orElseThrow();
            assertThat(saved.getGrade()).isEqualTo("초등 4");
            assertThat(saved.getGuardianPhone()).isEqualTo("02-123-4567");
            assertThat(saved.getClassName()).isEqualTo("오후반");
            assertThat(saved.getStudentPhone()).isEqualTo("01012345678");
            assertThat(saved.getName()).isEqualTo("김학생");
            assertThat(saved.getVersion()).isEqualTo(2);
        } finally {
            start.countDown();
            callers.shutdownNow();
            assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            students.deleteById(student.getId());
            users.deleteById(teacher.getId());
            academies.deleteById(academy.getId());
        }
    }

    private MvcResult update(
            MockMvc mvc, String token, Long studentId, String body, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
        }
        return mvc.perform(patch("/api/v1/students/{id}", studentId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }
}
