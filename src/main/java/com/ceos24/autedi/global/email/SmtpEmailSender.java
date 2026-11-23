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
    public void send(
            Long invitationId,
            String toEmail,
            String fromAddress,
            String fromName,
            String subject,
            String htmlContent) {
        String toDomain = EmailLogSupport.domainOf(toEmail);
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn(
                    "[email] 메일 설정이 없어 발송을 건너뜀: invitationId={}, toDomain={}, reason=MAIL_SENDER_MISSING",
                    invitationId,
                    toDomain);
            return;
        }

        if (mailSender instanceof JavaMailSenderImpl mailSenderImpl) {
            if (mailSenderImpl.getHost() == null
                    || mailSenderImpl.getHost().trim().isEmpty()
                    || mailSenderImpl.getUsername() == null
                    || mailSenderImpl.getUsername().trim().isEmpty()) {
                log.warn(
                        "[email] 메일 설정이 없어 발송을 건너뜀: invitationId={}, toDomain={}, reason=MAIL_HOST_OR_USER_MISSING",
                        invitationId,
                        toDomain);
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
            log.info("[email] 초대 발송 완료: invitationId={}, toDomain={}, via=smtp", invitationId, toDomain);
        } catch (Exception e) {
            // SMTP 예외 메시지에 수신자 주소가 들어갈 수 있어 메시지와 스택트레이스를 남기지 않고 종류만 남긴다.
            log.error(
                    "[email] 초대 발송 실패: invitationId={}, toDomain={}, via=smtp, errorType={}, causeType={}",
                    invitationId,
                    toDomain,
                    e.getClass().getSimpleName(),
                    EmailLogSupport.causeType(e));
        }
    }
}
