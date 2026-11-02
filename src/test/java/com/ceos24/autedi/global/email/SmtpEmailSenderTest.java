package com.ceos24.autedi.global.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender javaMailSender;

    private SmtpEmailSender smtpEmailSender;

    @BeforeEach
    void setUp() {
        smtpEmailSender = new SmtpEmailSender(mailSenderProvider);
    }

    @Test
    @DisplayName("JavaMailSender가 null인 경우 예외 없이 로그 기록 후 정상 리턴")
    void send_whenMailSenderNull_logsAndReturnsSafely() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        assertDoesNotThrow(
                () -> smtpEmailSender.send("teacher@test.com", "noreply@autedi.com", "AUTEDI", "초대장", "<p>내용</p>"));

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("JavaMailSenderImpl의 host 또는 username이 비어있는 경우 전송 생략")
    void send_whenHostOrUsernameBlank_skipsSending() {
        JavaMailSenderImpl implWithoutHost = new JavaMailSenderImpl();
        implWithoutHost.setHost("");
        when(mailSenderProvider.getIfAvailable()).thenReturn(implWithoutHost);

        assertDoesNotThrow(
                () -> smtpEmailSender.send("teacher@test.com", "noreply@autedi.com", "AUTEDI", "초대장", "<p>내용</p>"));

        JavaMailSenderImpl implWithoutUser = new JavaMailSenderImpl();
        implWithoutUser.setHost("smtp.gmail.com");
        implWithoutUser.setUsername("");
        when(mailSenderProvider.getIfAvailable()).thenReturn(implWithoutUser);

        assertDoesNotThrow(
                () -> smtpEmailSender.send("teacher@test.com", "noreply@autedi.com", "AUTEDI", "초대장", "<p>내용</p>"));

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("JavaMailSender가 정상 구성된 경우 MimeMessage 생성 및 발송")
    void send_whenConfigured_sendsMimeMessage() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);

        assertDoesNotThrow(
                () -> smtpEmailSender.send("teacher@test.com", "noreply@autedi.com", "AUTEDI", "초대장", "<p>내용</p>"));

        verify(javaMailSender, times(1)).send(mimeMessage);
    }
}
