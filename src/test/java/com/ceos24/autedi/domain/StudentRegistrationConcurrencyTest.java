package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class StudentRegistrationConcurrencyTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AcademyRepository academyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private com.ceos24.autedi.domain.academy.service.AcademySettingsService settings;

    @Autowired
    private com.ceos24.autedi.domain.academy.repository.AcademyGradeRepository gradeOptions;

    @Autowired
    private com.ceos24.autedi.domain.academy.repository.AcademyClassRepository classOptions;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @Timeout(40)
    @DisplayName("같은 기관·이름의 최초 등록이 동시에 들어와도 한 건만 생성하고 다른 요청은 후보를 반환한다")
    void concurrentUnconfirmedRequestsCreateOneStudent() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        Academy academy = academyRepository.saveAndFlush(
                Academy.builder().name("동시 등록 테스트 기관").build());
        User teacher = userRepository.saveAndFlush(User.builder()
                .academy(academy)
                .email(UUID.randomUUID() + "@student-concurrency.test")
                .passwordHash("hashed")
                .name("김선생")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());
        String token = "Bearer "
                + jwtTokenProvider.createAccessToken(
                        teacher.getId(), academy.getId(), teacher.getEmail(), Role.TEACHER.name());
        var selection = new AcademySelectionFixture(settings, academy.getId());
        String selectedBody = selection.request("{\"name\":\"김학생\",\"grade\":\"초등 3\"}");
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<MvcResult> register = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
                }
                return mockMvc.perform(post("/api/v1/students")
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(selectedBody))
                        .andReturn();
            };
            Future<MvcResult> first = callers.submit(register);
            Future<MvcResult> second = callers.submit(register);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MvcResult firstResult = first.get(15, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(15, TimeUnit.SECONDS);
            assertThat(new int[] {
                        firstResult.getResponse().getStatus(),
                        secondResult.getResponse().getStatus()
                    })
                    .containsExactlyInAnyOrder(201, 409);

            var students = studentRepository.findByAcademyIdAndNameOrderByIdAsc(academy.getId(), "김학생");
            assertThat(students).hasSize(1);
            MvcResult conflict = firstResult.getResponse().getStatus() == 409 ? firstResult : secondResult;
            var detail = objectMapper.readTree(conflict.getResponse().getContentAsByteArray());
            assertThat(detail.get("code").asText()).isEqualTo("STUDENT_002");
            assertThat(detail.at("/error/candidates/0/studentId").asLong())
                    .isEqualTo(students.getFirst().getId());
        } finally {
            start.countDown();
            callers.shutdownNow();
            assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            studentRepository.deleteAll(studentRepository.findByAcademyIdAndNameOrderByIdAsc(academy.getId(), "김학생"));
            userRepository.deleteById(teacher.getId());
            deleteSettings(academy.getId());
            academyRepository.deleteById(academy.getId());
        }
    }

    private void deleteSettings(Long academyId) {
        new org.springframework.transaction.support.TransactionTemplate(
                        context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .executeWithoutResult(tx -> {
                    classOptions.deleteByAcademyId(academyId);
                    gradeOptions.deleteByAcademyId(academyId);
                });
    }
}
