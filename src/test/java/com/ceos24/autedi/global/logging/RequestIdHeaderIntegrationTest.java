package com.ceos24.autedi.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** RequestIdFilter를 Spring Security 체인 앞에 둔 실제 설정으로 응답 헤더를 확인한다. */
@SpringBootTest
class RequestIdHeaderIntegrationTest {

    private static final String ID_PATTERN = "[0-9a-f]{8}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestIdFilter requestIdFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // addFilters를 먼저 호출해 requestId 필터가 Security 체인보다 앞에 오게 한다.
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("인증 없이 401로 끝난 요청에도 X-Request-Id 응답 헤더가 붙는다")
    void unauthorizedResponseHasRequestId() throws Exception {
        MockHttpServletResponse response =
                mockMvc.perform(get("/api/v1/worksheets")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).matches(ID_PATTERN);
    }

    @Test
    @DisplayName("클라이언트가 보낸 X-Request-Id는 무시하고 요청은 정상적으로 처리한다")
    void clientSuppliedRequestIdIsIgnored() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/v1/worksheets").header(RequestIdFilter.REQUEST_ID_HEADER, "client-supplied"))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isNotEqualTo("client-supplied")
                .matches(ID_PATTERN);
    }

    @Test
    @DisplayName("요청마다 서로 다른 X-Request-Id가 내려간다")
    void eachRequestGetsDifferentRequestId() throws Exception {
        String first = mockMvc.perform(get("/api/v1/worksheets"))
                .andReturn()
                .getResponse()
                .getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        String second = mockMvc.perform(get("/api/v1/worksheets"))
                .andReturn()
                .getResponse()
                .getHeader(RequestIdFilter.REQUEST_ID_HEADER);

        assertThat(first).isNotEqualTo(second);
    }
}
