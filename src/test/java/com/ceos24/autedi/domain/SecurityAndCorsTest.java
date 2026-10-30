package com.ceos24.autedi.domain;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class SecurityAndCorsTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private String validBasicAuthHeader;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        validBasicAuthHeader =
                "Basic " + Base64.getEncoder().encodeToString("autedi:autedi2026!".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("GET /autedi-docs 및 /v3/api-docs - 비인가 접근 시 HTTP Basic Auth 401 Unauthorized")
    void unauthorizedAccess_swaggerEndpoints_returns401() throws Exception {
        // /autedi-docs 비인가 접근 시 401
        mockMvc.perform(get("/autedi-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));

        // /v3/api-docs 비인가 접근 시 401
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("GET /autedi-docs 및 /v3/api-docs - 올바른 HTTP Basic 자격증명(autedi:autedi2026!) 전송 시 200 OK 또는 정상 응답")
    void authorizedAccess_swaggerEndpoints_withValidCredentials_success() throws Exception {
        // /autedi-docs는 Swagger UI 인덱스 페이지(/swagger-ui/index.html)로 정상 리다이렉트(302 Found)
        mockMvc.perform(get("/autedi-docs").header(HttpHeaders.AUTHORIZATION, validBasicAuthHeader))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "/swagger-ui/index.html"));

        // /v3/api-docs는 OpenAPI JSON 문서 정상 반환(200 OK)
        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, validBasicAuthHeader))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName(
            "OPTIONS /api/v1/worksheets - CORS Preflight 호출 시 Access-Control-Allow-Origin 및 Access-Control-Expose-Headers 반환 검증")
    void corsPreflight_returnsAllowedOriginAndExposedHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/worksheets")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "ETag, Authorization, Location"));
    }
}
