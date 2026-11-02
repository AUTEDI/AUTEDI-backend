package com.ceos24.autedi.global.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailSender emailSender;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.mail.from-address:noreply@autedi.com}")
    private String fromAddress;

    @Value("${app.mail.from-name:AUTEDI}")
    private String fromName;

    @Async("mailExecutor")
    public void sendInvitationEmail(String toEmail, String academyName, String rawToken) {
        String base = (frontendUrl != null && frontendUrl.endsWith("/"))
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : (frontendUrl != null ? frontendUrl : "http://localhost:3000");
        String inviteLink = base + "/signup?token=" + rawToken;
        log.info("[초대 링크 발송] To: {}, 학원: {}, 링크: {}", toEmail, academyName, inviteLink);

        String subject = "[" + academyName + "] 선생님 초대장";
        String htmlContent = buildInvitationHtml(academyName, inviteLink);

        emailSender.send(toEmail, fromAddress, fromName, subject, htmlContent);
    }

    private String buildInvitationHtml(String academyName, String inviteLink) {
        return "<!DOCTYPE html>"
                + "<html lang='ko'>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='margin:0;padding:20px;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif;'>"
                + "<div style='max-width:540px;margin:0 auto;background:#ffffff;border-radius:12px;border:1px solid #e2e8f0;padding:36px;box-shadow:0 4px 6px -1px rgba(0,0,0,0.05);'>"
                + "<div style='margin-bottom:24px;'>"
                + "<h2 style='margin:0 0 8px 0;color:#0f172a;font-size:22px;font-weight:700;'>선생님 초대장</h2>"
                + "<p style='margin:0;color:#64748b;font-size:14px;'>AUTEDI 초등 영어 학습지 AI 첨삭 서비스</p>"
                + "</div>"
                + "<div style='line-height:1.7;color:#334155;font-size:15px;margin-bottom:32px;'>"
                + "<p style='margin:0 0 16px 0;'>안녕하세요.<br><strong style='color:#0f172a;'>" + academyName
                + "</strong>에 선생님으로 초대되었습니다.</p>"
                + "<p style='margin:0 0 16px 0;'>아래 버튼을 클릭하여 회원가입을 완료해 주시면, 소속 학원의 학습지 첨삭 및 코멘트 작성 업무를 시작하실 수 있습니다.</p>"
                + "<p style='margin:0;color:#ef4444;font-size:13px;'>* 본 초대 링크는 발송 시점으로부터 24시간 동안만 유효합니다.</p>"
                + "</div>"
                + "<div style='text-align:center;margin-bottom:32px;'>"
                + "<a href='" + inviteLink
                + "' target='_blank' style='display:inline-block;padding:14px 28px;background-color:#2563eb;color:#ffffff;text-decoration:none;font-weight:600;font-size:15px;border-radius:8px;box-shadow:0 2px 4px rgba(37,99,235,0.2);'>초대 수락하고 회원가입하기</a>"
                + "</div>"
                + "<div style='border-top:1px solid #e2e8f0;padding-top:20px;font-size:12px;color:#94a3b8;line-height:1.6;'>"
                + "<p style='margin:0 0 8px 0;'>버튼이 동작하지 않을 경우 아래 링크를 브라우저 주소창에 직접 붙여넣어 주세요:</p>"
                + "<p style='margin:0;word-break:break-all;'><a href='" + inviteLink
                + "' style='color:#2563eb;text-decoration:underline;'>" + inviteLink + "</a></p>"
                + "<p style='margin:16px 0 0 0;'>본 메일은 " + academyName + "의 원장 선생님 요청으로 발송되었습니다.</p>"
                + "</div>"
                + "</div>"
                + "</body>"
                + "</html>";
    }
}
