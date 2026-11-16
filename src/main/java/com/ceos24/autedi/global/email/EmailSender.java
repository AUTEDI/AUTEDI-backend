package com.ceos24.autedi.global.email;

public interface EmailSender {
    void send(String toEmail, String fromAddress, String fromName, String subject, String htmlContent);
}
