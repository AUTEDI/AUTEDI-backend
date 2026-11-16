package com.ceos24.autedi.global.email;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class HttpEmailSender implements EmailSender {

    private final RestClient restClient;
    private final String apiKey;
    private final String endpoint;
    private final String defaultHttpFrom;

    @Override
    public void send(String toEmail, String fromAddress, String fromName, String subject, String htmlContent) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn(
                    "HttpEmailSender API 키(RESEND_API_KEY)가 설정되지 않아 메일 발송을 로깅으로 대체합니다. (To: {}, Subject: {})",
                    toEmail,
                    subject);
            return;
        }

        String actualFrom = (fromAddress != null && fromAddress.contains("@")) ? fromAddress : defaultHttpFrom;
        String formattedFrom = (fromName != null && !fromName.trim().isEmpty())
                ? fromName.trim() + " <" + actualFrom.trim() + ">"
                : actualFrom.trim();

        Map<String, Object> payload = Map.of(
                "from", formattedFrom,
                "to", List.of(toEmail),
                "subject", subject,
                "html", htmlContent);

        try {
            restClient
                    .post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("HTTP 초대 이메일 발송 성공 (HTTPS 443 포트): to={}, endpoint={}", toEmail, endpoint);
        } catch (Exception e) {
            log.error("HTTP 초대 이메일 발송 실패: to={}, error={}", toEmail, e.getMessage(), e);
        }
    }
}
