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
    public void send(
            Long invitationId,
            String toEmail,
            String fromAddress,
            String fromName,
            String subject,
            String htmlContent) {
        String toDomain = EmailLogSupport.domainOf(toEmail);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn(
                    "[email] 메일 설정이 없어 발송을 건너뜀: invitationId={}, toDomain={}, reason=API_KEY_MISSING",
                    invitationId,
                    toDomain);
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

            log.info("[email] 초대 발송 완료: invitationId={}, toDomain={}, via=http", invitationId, toDomain);
        } catch (Exception e) {
            // 예외 메시지에 수신자 주소가 들어갈 수 있어 메시지와 스택트레이스를 남기지 않고 종류만 남긴다.
            log.error(
                    "[email] 초대 발송 실패: invitationId={}, toDomain={}, via=http, errorType={}, causeType={}",
                    invitationId,
                    toDomain,
                    e.getClass().getSimpleName(),
                    EmailLogSupport.causeType(e));
        }
    }
}
