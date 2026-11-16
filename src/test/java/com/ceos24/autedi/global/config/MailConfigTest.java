package com.ceos24.autedi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceos24.autedi.global.email.EmailSender;
import com.ceos24.autedi.global.email.HttpEmailSender;
import com.ceos24.autedi.global.email.SmtpEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MailConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(MailConfig.class);

    @Test
    @DisplayName("app.mail.type 설정이 없으면 기본으로 SmtpEmailSender 빈이 등록된다")
    void whenMailTypeMissing_thenSmtpEmailSenderIsCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmailSender.class);
            assertThat(context.getBean(EmailSender.class)).isInstanceOf(SmtpEmailSender.class);
        });
    }

    @Test
    @DisplayName("app.mail.type=http 인 경우 HttpEmailSender 빈이 등록된다")
    void whenMailTypeIsHttp_thenHttpEmailSenderIsCreated() {
        contextRunner.withPropertyValues("app.mail.type=http").run(context -> {
            assertThat(context).hasSingleBean(EmailSender.class);
            assertThat(context.getBean(EmailSender.class)).isInstanceOf(HttpEmailSender.class);
        });
    }

    @Test
    @DisplayName("app.mail.type=smtp 인 경우 SmtpEmailSender 빈이 등록된다")
    void whenMailTypeIsSmtp_thenSmtpEmailSenderIsCreated() {
        contextRunner.withPropertyValues("app.mail.type=smtp").run(context -> {
            assertThat(context).hasSingleBean(EmailSender.class);
            assertThat(context.getBean(EmailSender.class)).isInstanceOf(SmtpEmailSender.class);
        });
    }
}
