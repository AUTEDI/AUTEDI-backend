package com.ceos24.autedi.global.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private EmailSender emailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(emailSender);
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@autedi.com");
        ReflectionTestUtils.setField(emailService, "fromName", "AUTEDI");
    }

    @Test
    @DisplayName("초대 이메일 발송 시 EmailSender.send 호출 및 링크 끝 슬래시 자동 정돈 검증")
    void sendInvitationEmail_callsEmailSenderWithSanitizedLink() {
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://autedi-prototype.vercel.app/");

        assertDoesNotThrow(() -> emailService.sendInvitationEmail("teacher@test.com", "에이플러스 학원", "raw-token-123"));

        verify(emailSender, times(1))
                .send(
                        eq("teacher@test.com"),
                        eq("noreply@autedi.com"),
                        eq("AUTEDI"),
                        eq("[에이플러스 학원] 선생님 초대장"),
                        contains("https://autedi-prototype.vercel.app/signup?token=raw-token-123"));
    }
}
