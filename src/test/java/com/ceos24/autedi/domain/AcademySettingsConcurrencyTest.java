package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.academy.service.AcademySettingsService;
import com.ceos24.autedi.domain.user.entity.*;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Timeout(40)
class AcademySettingsConcurrencyTest {
    private static final String ROOT = "/api/v1/academies/me";

    @Autowired
    WebApplicationContext context;

    @Autowired
    AcademyRepository academies;

    @Autowired
    UserRepository users;

    @Autowired
    AcademySettingsService settings;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    JdbcTemplate jdbc;

    private MockMvc mvc;
    private Academy academy;
    private String token;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academies.saveAndFlush(Academy.builder().name("설정 동시성 학원").build());
        var user = users.saveAndFlush(User.builder()
                .academy(academy)
                .name("원장")
                .email(UUID.randomUUID() + "@settings.test")
                .passwordHash("hash")
                .role(Role.DIRECTOR)
                .status(UserStatus.ACTIVE)
                .build());
        token = "Bearer "
                + tokens.createAccessToken(user.getId(), academy.getId(), user.getEmail(), Role.DIRECTOR.name());
    }

    @AfterEach
    void cleanUp() {
        for (String table : List.of("students", "users", "academy_classes", "academy_grades"))
            jdbc.update("delete from " + table + " where academy_id=?", academy.getId());
        academies.deleteById(academy.getId());
    }

    private List<MvcResult> race(MockHttpServletRequestBuilder first, MockHttpServletRequestBuilder second)
            throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> a = () -> invoke(first, ready, start);
            Callable<MvcResult> b = () -> invoke(second, ready, start);
            var one = pool.submit(a);
            var two = pool.submit(b);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MvcResult invoke(MockHttpServletRequestBuilder request, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시 요청 시작 시간 초과");
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andReturn();
    }

    @Test
    void twoInitialReadsCreateOnlyOneDefaultSet() throws Exception {
        var results = race(get(ROOT + "/settings"), get(ROOT + "/settings"));
        assertThat(results)
                .allSatisfy(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
        var json = new ObjectMapper();
        assertThat(json.readTree(results.get(0).getResponse().getContentAsByteArray())
                        .get("result"))
                .isEqualTo(json.readTree(results.get(1).getResponse().getContentAsByteArray())
                        .get("result"));
        assertThat(settings.get(academy.getId()).grades()).hasSize(9);
    }

    @Test
    void concurrentDuplicateClassCreationReturnsOneCreatedAndOneConflict() throws Exception {
        var results = race(
                post(ROOT + "/classes").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" A반 \"}"),
                post(ROOT + "/classes").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A반\"}"));
        assertThat(results.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(201, 409);
        assertThat(settings.get(academy.getId()).classes()).hasSize(1);
    }

    @Test
    void classDeletionAndStudentAssignmentCannotLeaveAnInvalidReference() throws Exception {
        long grade = settings.get(academy.getId()).grades().getFirst().gradeId();
        settings.selectGrades(academy.getId(), List.of(grade));
        long schoolClass = settings.createClass(academy.getId(), "A반").classId();
        var results = race(
                delete(ROOT + "/classes/{id}", schoolClass),
                post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"학생\",\"gradeId\":" + grade + ",\"classId\":" + schoolClass + "}"));
        int deleted = results.get(0).getResponse().getStatus(),
                registered = results.get(1).getResponse().getStatus();
        if (registered == 201) {
            assertThat(deleted).isEqualTo(409);
            assertThat(settings.get(academy.getId()).classes()).hasSize(1);
        } else {
            assertThat(registered).isEqualTo(404);
            assertThat(deleted).isEqualTo(204);
            assertThat(settings.get(academy.getId()).classes()).isEmpty();
        }
        assertThat(jdbc.queryForObject("select count(*) from students where academy_id=?", Long.class, academy.getId()))
                .isEqualTo(registered == 201 ? 1 : 0);
    }
}
