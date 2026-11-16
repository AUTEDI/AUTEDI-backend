package com.ceos24.autedi.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(
        properties = {
            "app.qr.rate-limit.scan-per-minute=3",
            "app.qr.rate-limit.token-per-minute=2",
            "app.qr.rate-limit.status-per-minute=5",
            "app.qr.rate-limit.link-per-minute=2"
        })
class QrRateLimitTest {

    private static final String BASE = "/api/v1/auth/qr-sessions";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private int call(String path, String ip) throws Exception {
        RequestPostProcessor from = r -> {
            r.setRemoteAddr(ip);
            return r;
        };
        return mockMvc.perform(post(BASE + path)
                        .with(from)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"nope\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    @DisplayName("한도 초과 시 429 + Retry-After, 한도 이내 요청은 정상 처리(404)")
    void exceedsLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("/scan", "10.0.0.1")).isEqualTo(404);
        }
        mockMvc.perform(post(BASE + "/scan")
                        .with(r -> {
                            r.setRemoteAddr("10.0.0.1");
                            return r;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"nope\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("COMMON_429"));
    }

    @Test
    @DisplayName("IP별·경로별로 독립된 버킷을 사용한다")
    void bucketsAreIndependent() throws Exception {
        for (int i = 0; i < 2; i++) {
            assertThat(call("/token", "10.0.0.2")).isEqualTo(404);
        }
        assertThat(call("/token", "10.0.0.2")).isEqualTo(429);
        // 같은 IP, 다른 경로
        assertThat(call("/status", "10.0.0.2")).isEqualTo(404);
        // 다른 IP, 같은 경로
        assertThat(call("/token", "10.0.0.3")).isEqualTo(404);
    }

    @Test
    @DisplayName("인증이 필요한 create는 rate limit 대상이 아니다 (401)")
    void createNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post(BASE)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("link도 scan과 같은 방식으로 rate limit이 적용된다 (한도 이내는 인증 오류 401, 초과 시 429)")
    void linkIsLimited() throws Exception {
        for (int i = 0; i < 2; i++) {
            assertThat(call("/link", "10.0.0.9")).isEqualTo(401);
        }
        assertThat(call("/link", "10.0.0.9")).isEqualTo(429);
        // 다른 IP는 독립 버킷
        assertThat(call("/link", "10.0.0.10")).isEqualTo(401);
    }
}
