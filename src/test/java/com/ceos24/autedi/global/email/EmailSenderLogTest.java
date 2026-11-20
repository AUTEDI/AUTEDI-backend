package com.ceos24.autedi.global.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 이메일 발송 로그에 주소·예외 메시지·스택트레이스가 남지 않고 컨벤션 형식으로 남는지 확인한다. */
class EmailSenderLogTest {

    private static final String TO = "teacher@secret-school.com";
    private static final String ENDPOINT = "https://api.resend.com/emails";

    private Logger httpLogger;
    private Logger smtpLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        logs = new ListAppender<>();
        logs.start();
        httpLogger = (Logger) LoggerFactory.getLogger(HttpEmailSender.class);
        smtpLogger = (Logger) LoggerFactory.getLogger(SmtpEmailSender.class);
        httpLogger.addAppender(logs);
        smtpLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        httpLogger.detachAppender(logs);
        smtpLogger.detachAppender(logs);
        logs.stop();
    }

    // ---- HTTP ----

    @Test
    @DisplayName("HTTP: API 키가 없으면 WARN 한 줄로 건너뛰고 주소는 남지 않는다")
    void httpApiKeyMissing() {
        HttpEmailSender sender = new HttpEmailSender(RestClient.builder().build(), "", ENDPOINT, "from@autedi.com");

        sender.send(5L, TO, "noreply@autedi.com", "AUTEDI", "[학원] 초대장", "<p>x</p>");

        assertOneLog(
                "WARN",
                "[email] 메일 설정이 없어 발송을 건너뜀: invitationId=5, toDomain=secret-school.com, reason=API_KEY_MISSING");
    }

    @Test
    @DisplayName("HTTP: 성공하면 INFO 한 줄에 invitationId, toDomain, via=http만 남는다")
    void httpSuccess() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        HttpEmailSender sender = new HttpEmailSender(builder.build(), "key", ENDPOINT, "from@autedi.com");

        sender.send(5L, TO, "noreply@autedi.com", "AUTEDI", "[학원] 초대장", "<p>x</p>");

        assertOneLog("INFO", "[email] 초대 발송 완료: invitationId=5, toDomain=secret-school.com, via=http");
    }

    @Test
    @DisplayName("HTTP: 실패하면 ERROR 한 줄에 errorType, causeType만 남고 주소·메시지·스택트레이스는 없다")
    void httpFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT)).andRespond(withException(new IOException("550 rejected " + TO)));
        HttpEmailSender sender = new HttpEmailSender(builder.build(), "key", ENDPOINT, "from@autedi.com");

        sender.send(5L, TO, "noreply@autedi.com", "AUTEDI", "[학원] 초대장", "<p>x</p>");

        assertOneLog(
                "ERROR",
                "[email] 초대 발송 실패: invitationId=5, toDomain=secret-school.com, via=http,"
                        + " errorType=ResourceAccessException, causeType=IOException");
    }

    // ---- SMTP ----

    @Test
    @DisplayName("SMTP: JavaMailSender가 없으면 WARN reason=MAIL_SENDER_MISSING")
    @SuppressWarnings("unchecked")
    void smtpSenderMissing() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        new SmtpEmailSender(provider).send(5L, TO, "noreply@autedi.com", "AUTEDI", "[학원] 초대장", "<p>x</p>");

        assertOneLog(
                "WARN",
                "[email] 메일 설정이 없어 발송을 건너뜀: invitationId=5, toDomain=secret-school.com,"
                        + " reason=MAIL_SENDER_MISSING");
    }

    @Test
    @DisplayName("SMTP: 호스트나 계정이 비어 있으면 WARN reason=MAIL_HOST_OR_USER_MISSING")
    @SuppressWarnings("unchecked")
    void smtpHostOrUserMissing() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        JavaMailSenderImpl withoutHost = new JavaMailSenderImpl();
        withoutHost.setHost("");
        when(provider.getIfAvailable()).thenReturn(withoutHost);

        new SmtpEmailSender(provider).send(5L, TO, "noreply@autedi.com", "AUTEDI", "[학원] 초대장", "<p>x</p>");

        assertOneLog(
                "WARN",
                "[email] 메일 설정이 없어 발송을 건너뜀: invitationId=5, toDomain=secret-school.com,"
                        + " reason=MAIL_HOST_OR_USER_MISSING");
    }

    @Test
    @DisplayName("SMTP: 성공하면 INFO 한 줄에 invitationId, toDomain, via=smtp만 남는다")
    @SuppressWarnings("unchecked")
    void smtpSuccess() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        when(provider.getIfAvailable()).thenReturn(mailSender);

        new SmtpEmailSender(provider).send(5L, TO, "noreply@autedi.com", "AUTEDI", "[학원] 초대장", "<p>x</p>");

        assertOneLog("INFO", "[email] 초대 발송 완료: invitationId=5, toDomain=secret-school.com, via=smtp");
    }

    @Test
    @DisplayName("SMTP: 실패하면 ERROR 한 줄에 errorType, causeType만 남고 주소·메시지·스택트레이스는 없다")
    @SuppressWarnings("unchecked")
    void smtpFailure() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("550 user " + TO + " unknown", new MessagingException("550 " + TO)))
                .when(mailSender)
                .send(any(MimeMessage.class));
        when(provider.getIfAvailable()).thenReturn(mailSender);

        new SmtpEmailSender(provider).send(5L, TO, "noreply@autedi.com", "AUTEDI", "[학원] 초대장", "<p>x</p>");

        assertOneLog(
                "ERROR",
                "[email] 초대 발송 실패: invitationId=5, toDomain=secret-school.com, via=smtp,"
                        + " errorType=MailSendException, causeType=MessagingException");
    }

    private void assertOneLog(String level, String expectedMessage) {
        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo(level);
        assertThat(event.getFormattedMessage()).isEqualTo(expectedMessage);
        assertThat(event.getFormattedMessage()).doesNotContain("teacher@").doesNotContain("[학원]");
        assertThat(event.getThrowableProxy()).isNull();
    }
}
