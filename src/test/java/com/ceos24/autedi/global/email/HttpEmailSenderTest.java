package com.ceos24.autedi.global.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpEmailSenderTest {

    @Test
    @DisplayName("API 키가 null이거나 비어 있는 경우 HTTP 호출 없이 안전하게 종료")
    void send_whenApiKeyBlank_skipsHttpCall() {
        RestClient restClient = RestClient.builder().build();
        HttpEmailSender sender =
                new HttpEmailSender(restClient, "", "https://api.resend.com/emails", "onboarding@resend.dev");

        assertDoesNotThrow(() -> sender.send("teacher@test.com", "noreply@autedi.com", "AUTEDI", "초대장", "<p>내용</p>"));
    }

    @Test
    @DisplayName("API 키가 설정된 경우 엔드포인트로 Bearer 토큰 및 JSON 전송")
    void send_whenApiKeyConfigured_sendsHttpPostRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        HttpEmailSender sender = new HttpEmailSender(
                restClient, "re_test_key_123", "https://api.resend.com/emails", "onboarding@resend.dev");

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer re_test_key_123"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.to[0]").value("teacher@test.com"))
                .andExpect(jsonPath("$.from").value("AUTEDI <noreply@autedi.com>"))
                .andExpect(jsonPath("$.subject").value("초대장"))
                .andRespond(withSuccess("{\"id\": \"msg_123\"}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> sender.send("teacher@test.com", "noreply@autedi.com", "AUTEDI", "초대장", "<p>내용</p>"));

        server.verify();
    }
}
