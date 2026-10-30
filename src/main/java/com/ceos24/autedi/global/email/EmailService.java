package com.ceos24.autedi.global.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Async
    public void sendInvitationEmail(String toEmail, String academyName, String rawToken) {
        String inviteLink = frontendUrl + "/signup?token=" + rawToken;
        log.info("[초대 링크 발송] To: {}, 학원: {}, 링크: {}", toEmail, academyName, inviteLink);

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender가 설정되지 않아 메일 발송을 로깅으로 대체합니다.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("[" + academyName + "] 선생님 초대장");
            message.setText("안녕하세요. " + academyName + "에 선생님으로 초대되었습니다.\n\n"
                    + "아래 링크를 클릭하여 24시간 이내에 회원가입을 완료해 주세요:\n"
                    + inviteLink + "\n\n"
                    + "감사합니다.");

            mailSender.send(message);
            log.info("초대 이메일 발송 성공: to={}", toEmail);
        } catch (Exception e) {
            log.error("초대 이메일 발송 실패: to={}, error={}", toEmail, e.getMessage(), e);
        }
    }
}
