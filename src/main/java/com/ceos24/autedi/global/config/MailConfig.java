package com.ceos24.autedi.global.config;

import com.ceos24.autedi.global.email.EmailSender;
import com.ceos24.autedi.global.email.HttpEmailSender;
import com.ceos24.autedi.global.email.SmtpEmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class MailConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.mail.type", havingValue = "http")
    public EmailSender httpEmailSender(
            @Value("${app.mail.http.api-key:${RESEND_API_KEY:}}") String apiKey,
            @Value("${app.mail.http.endpoint:${MAIL_HTTP_ENDPOINT:https://api.resend.com/emails}}") String endpoint,
            @Value("${app.mail.http.from:${MAIL_HTTP_FROM:onboarding@resend.dev}}") String defaultHttpFrom) {
        log.info("이메일 발송 빈 활성화: HttpEmailSender (HTTPS 443 포트 사용, endpoint={})", endpoint);
        return new HttpEmailSender(RestClient.builder().build(), apiKey, endpoint, defaultHttpFrom);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender smtpEmailSender(ObjectProvider<JavaMailSender> mailSenderProvider) {
        log.info("이메일 발송 빈 활성화: SmtpEmailSender (표준 SMTP 587/465 포트 사용)");
        return new SmtpEmailSender(mailSenderProvider);
    }
}
