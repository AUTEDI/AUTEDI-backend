package com.ceos24.autedi.global.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

@Slf4j
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Override
    public void send(String toEmail, String fromAddress, String fromName, String subject, String htmlContent) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender가 설정되지 않아 메일 발송을 로깅으로 대체합니다. (To: {}, Subject: {})", toEmail, subject);
            return;
        }

        if (mailSender instanceof JavaMailSenderImpl mailSenderImpl) {
            if (mailSenderImpl.getHost() == null
                    || mailSenderImpl.getHost().trim().isEmpty()
                    || mailSenderImpl.getUsername() == null
                    || mailSenderImpl.getUsername().trim().isEmpty()) {
                log.warn(
                        "메일 발송 호스트 또는 인증 계정(MAIL_USERNAME)이 설정되지 않아 메일 발송을 로깅으로 대체합니다. (To: {}, Subject: {})",
                        toEmail,
                        subject);
                return;
            }
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            String senderAddress = (fromAddress != null && !fromAddress.trim().isEmpty())
                    ? fromAddress
                    : (mailSender instanceof JavaMailSenderImpl impl && impl.getUsername() != null
                            ? impl.getUsername()
                            : "noreply@autedi.com");

            helper.setTo(toEmail);
            helper.setFrom(senderAddress, fromName);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("SMTP 초대 이메일 발송 성공 (포트 587/465): to={}", toEmail);
        } catch (Exception e) {
            log.error("SMTP 초대 이메일 발송 실패: to={}, error={}", toEmail, e.getMessage(), e);
        }
    }
}
