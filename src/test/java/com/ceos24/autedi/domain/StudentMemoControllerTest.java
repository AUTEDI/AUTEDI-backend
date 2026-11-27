package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.entity.StudentMemo;
import com.ceos24.autedi.domain.student.repository.StudentMemoRepository;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class StudentMemoControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AcademyRepository academies;

    @Autowired
    private UserRepository users;

    @Autowired
    private StudentRepository students;

    @Autowired
    private StudentMemoRepository memos;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private Academy academy;
    private User teacher;
    private User peer;
    private User director;
    private Student student;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academies.saveAndFlush(Academy.builder().name("메모 기관").build());
        teacher = user(academy, "teacher", "김선생", Role.TEACHER);
        peer = user(academy, "peer", "이선생", Role.TEACHER);
        director = user(academy, "director", "박원장", Role.DIRECTOR);
        student = student(academy, "김학생");
    }

    @Test
    @DisplayName("선생님이 메모를 등록하면 201과 작성자 이름이 담긴 메모를 받고 저장된다")
    void teacherCreatesMemo() throws Exception {
        MvcResult result = mvc.perform(create(student.getId(), "  다음 주 시험 범위를 안내함  ", teacher))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andReturn();

        JsonNode body = body(result);
        assertThat(body.get("memoId").asLong()).isPositive();
        assertThat(body.get("content").asText()).isEqualTo("다음 주 시험 범위를 안내함");
        assertThat(body.get("authorName").asText()).isEqualTo("김선생");
        assertThat(body.get("createdAt").isNull()).isFalse();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();

        em.flush();
        em.clear();
        StudentMemo saved = memos.findById(body.get("memoId").asLong()).orElseThrow();
        assertThat(saved.getStudent().getId()).isEqualTo(student.getId());
        assertThat(saved.getAuthor().getId()).isEqualTo(teacher.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("원장도 메모를 등록할 수 있다")
    void directorCreatesMemo() throws Exception {
        MvcResult result = mvc.perform(create(student.getId(), "원장 메모", director))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(body(result).get("authorName").asText()).isEqualTo("박원장");
    }

    @Test
    @DisplayName("메모는 최신순으로 조회되고 같은 기관의 다른 교사가 쓴 메모도 함께 보인다")
    void listsNewestFirstIncludingOtherTeachers() throws Exception {
        StudentMemo oldest = memo(student, teacher, "가장 오래된 메모");
        StudentMemo newest = memo(student, peer, "가장 최근 메모");
        StudentMemo middle = memo(student, director, "중간 메모");
        // 저장 순서와 작성 시각 순서가 다른 경우에도 작성 시각이 우선이다.
        setCreatedAt(oldest, "2026-01-01 09:00:00");
        setCreatedAt(middle, "2026-02-01 09:00:00");
        setCreatedAt(newest, "2026-03-01 09:00:00");

        MvcResult result = mvc.perform(list(student.getId(), "", teacher))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = body(result).get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("content").asText()).isEqualTo("가장 최근 메모");
        assertThat(items.get(0).get("authorName").asText()).isEqualTo("이선생");
        assertThat(items.get(1).get("content").asText()).isEqualTo("중간 메모");
        assertThat(items.get(2).get("content").asText()).isEqualTo("가장 오래된 메모");
        assertThat(body(result).get("total").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("작성 시각이 같으면 나중에 저장된 메모가 먼저 나온다")
    void sameTimestampOrdersByIdDescending() throws Exception {
        StudentMemo first = memo(student, teacher, "먼저");
        StudentMemo second = memo(student, teacher, "나중");
        setCreatedAt(first, "2026-03-01 09:00:00");
        setCreatedAt(second, "2026-03-01 09:00:00");

        JsonNode items = body(mvc.perform(list(student.getId(), "", teacher))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("items");

        assertThat(items.get(0).get("content").asText()).isEqualTo("나중");
        assertThat(items.get(1).get("content").asText()).isEqualTo("먼저");
    }

    @Test
    @DisplayName("페이지 나누기는 page·size에 따라 전체 개수와 페이지 수를 함께 준다")
    void paginates() throws Exception {
        for (int i = 1; i <= 5; i++) {
            StudentMemo memo = memo(student, teacher, "메모 " + i);
            setCreatedAt(memo, "2026-03-0" + i + " 09:00:00");
        }

        JsonNode second = body(mvc.perform(list(student.getId(), "?page=1&size=2", teacher))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(second.get("items")).hasSize(2);
        assertThat(second.get("items").get(0).get("content").asText()).isEqualTo("메모 3");
        assertThat(second.get("items").get(1).get("content").asText()).isEqualTo("메모 2");
        assertThat(second.get("total").asLong()).isEqualTo(5);
        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(second.get("size").asInt()).isEqualTo(2);
        assertThat(second.get("totalPages").asInt()).isEqualTo(3);

        JsonNode beyond = body(mvc.perform(list(student.getId(), "?page=9&size=2", teacher))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(beyond.get("items")).isEmpty();
        assertThat(beyond.get("total").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("메모가 없는 학생은 빈 목록이고, 다른 학생의 메모는 섞이지 않는다")
    void emptyListAndOtherStudentsMemosAreExcluded() throws Exception {
        Student other = student(academy, "다른학생");
        memo(other, teacher, "다른 학생 메모");

        JsonNode result = body(mvc.perform(list(student.getId(), "", teacher))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(result.get("items")).isEmpty();
        assertThat(result.get("total").asLong()).isZero();
    }

    @Test
    @DisplayName("퇴사한 작성자의 메모도 이름이 그대로 표시된다")
    void resignedAuthorNameIsKept() throws Exception {
        memo(student, peer, "퇴사 전 메모");
        em.flush();
        jdbc.update("UPDATE users SET status = 'RESIGNED' WHERE id = ?", peer.getId());
        em.clear();

        JsonNode items = body(mvc.perform(list(student.getId(), "", teacher))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("items");

        assertThat(items.get(0).get("authorName").asText()).isEqualTo("이선생");
    }

    @Test
    @DisplayName("다른 기관 사용자는 조회·등록 모두 404이고 메모가 만들어지지 않는다")
    void otherAcademyIsHidden() throws Exception {
        Academy otherAcademy =
                academies.saveAndFlush(Academy.builder().name("다른 기관").build());
        User outsider = user(otherAcademy, "outsider", "외부인", Role.DIRECTOR);

        mvc.perform(list(student.getId(), "", outsider))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"));
        mvc.perform(create(student.getId(), "침입 메모", outsider))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"));

        assertThat(memos.count()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 학생은 404이다")
    void missingStudentIsNotFound() throws Exception {
        mvc.perform(list(Long.MAX_VALUE, "", teacher))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"));
        mvc.perform(create(Long.MAX_VALUE, "메모", teacher))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("비어 있거나 공백뿐인 내용은 400이다")
    void blankContentIsRejected(String content) throws Exception {
        mvc.perform(create(student.getId(), content, teacher))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        assertThat(memos.count()).isZero();
    }

    @Test
    @DisplayName("내용이 없는 요청과 JSON이 아닌 요청은 400이다")
    void missingContentIsRejected() throws Exception {
        mvc.perform(post("/api/v1/students/{id}/memos", student.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        mvc.perform(post("/api/v1/students/{id}/memos", student.getId())
                        .header(HttpHeaders.AUTHORIZATION, token(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("메모"))
                .andExpect(status().isBadRequest());

        assertThat(memos.count()).isZero();
    }

    @Test
    @DisplayName("내용은 1,000자까지 허용하고 1,001자부터 400이다")
    void contentLengthLimit() throws Exception {
        mvc.perform(create(student.getId(), "가".repeat(1000), teacher)).andExpect(status().isCreated());
        mvc.perform(create(student.getId(), "가".repeat(1001), teacher))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        assertThat(memos.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?page=abc"})
    @DisplayName("잘못된 page·size는 400이다")
    void invalidPagingIsRejected(String query) throws Exception {
        mvc.perform(list(student.getId(), query, teacher))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("인증 없이는 조회·등록할 수 없다")
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/students/{id}/memos", student.getId())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/students/{id}/memos", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"메모\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(memos.count()).isZero();
    }

    private RequestBuilder create(Long studentId, String content, User user) throws Exception {
        return post("/api/v1/students/{id}/memos", studentId)
                .header(HttpHeaders.AUTHORIZATION, token(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("content", content)));
    }

    private RequestBuilder list(Long studentId, String query, User user) {
        return get("/api/v1/students/{id}/memos" + query, studentId).header(HttpHeaders.AUTHORIZATION, token(user));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray()).get("result");
    }

    private StudentMemo memo(Student target, User author, String content) {
        return memos.saveAndFlush(StudentMemo.builder()
                .student(target)
                .author(author)
                .content(content)
                .build());
    }

    private void setCreatedAt(StudentMemo memo, String timestamp) {
        em.flush();
        jdbc.update("UPDATE student_memos SET created_at = ? WHERE id = ?", Timestamp.valueOf(timestamp), memo.getId());
        em.clear();
    }

    private Student student(Academy owner, String name) {
        return students.saveAndFlush(
                Student.builder().academy(owner).name(name).grade("초등 3").build());
    }

    private User user(Academy owner, String suffix, String name, Role role) {
        return users.saveAndFlush(User.builder()
                .academy(owner)
                .email("memo_" + suffix + "@test.com")
                .passwordHash("hashed")
                .name(name)
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
