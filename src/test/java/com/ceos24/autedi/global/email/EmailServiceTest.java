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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender javaMailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSenderProvider);
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@autedi.com");
        ReflectionTestUtils.setField(emailService, "fromName", "AUTEDI");
    }

    @Test
    @DisplayName("JavaMailSender가 null인 경우 예외 없이 로그만 남기고 정상 종료")
    void sendInvitationEmail_whenMailSenderNull_logsAndReturnsSafely() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        assertDoesNotThrow(() -> emailService.sendInvitationEmail("teacher@test.com", "에이플러스 학원", "raw-token-123"));

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("JavaMailSenderImpl의 host 또는 username이 비어 있는 경우 메일 발송 생략")
    void sendInvitationEmail_whenHostOrUsernameBlank_skipsSending() {
        JavaMailSenderImpl implWithoutHost = new JavaMailSenderImpl();
        implWithoutHost.setHost("");
        when(mailSenderProvider.getIfAvailable()).thenReturn(implWithoutHost);

        assertDoesNotThrow(() -> emailService.sendInvitationEmail("teacher@test.com", "에이플러스 학원", "raw-token-123"));

        JavaMailSenderImpl implWithoutUser = new JavaMailSenderImpl();
        implWithoutUser.setHost("smtp.gmail.com");
        implWithoutUser.setUsername("");
        when(mailSenderProvider.getIfAvailable()).thenReturn(implWithoutUser);

        assertDoesNotThrow(() -> emailService.sendInvitationEmail("teacher@test.com", "에이플러스 학원", "raw-token-123"));

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("JavaMailSender가 정상 주입된 경우 MimeMessage 전송 호출")
    void sendInvitationEmail_whenConfigured_sendsMimeMessage() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);

        assertDoesNotThrow(() -> emailService.sendInvitationEmail("teacher@test.com", "에이플러스 학원", "raw-token-123"));

        verify(javaMailSender, times(1)).send(mimeMessage);
    }
}
