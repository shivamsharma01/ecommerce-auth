package com.mcart.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationEmailService {

    private final JavaMailSender mailSender;

    @Value("${auth.verification.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${spring.mail.username:noreply@localhost}")
    private String fromEmail;

    private static final String SUBJECT = "Verify your email";
    private static final String VERIFY_PATH = "/auth/verify-email";

    /**
     * Sends a verification email with a link to confirm the user's email address.
     *
     * @param to    recipient email address
     * @param token verification token (included in the link)
     * @throws MessagingException if the email could not be sent
     */
    public void sendVerificationEmail(String to, String token) throws MessagingException {
        String verificationLink = baseUrl + VERIFY_PATH + "?token=" + token;

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(SUBJECT);
        helper.setText(buildEmailBody(verificationLink), true);

        mailSender.send(message);
        log.info("Verification email sent to {}", to);
    }

    private String buildEmailBody(String verificationLink) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <h2>Verify your email address</h2>
                <p>Thanks for signing up! Please click the link below to verify your email address:</p>
                <p><a href="%s" style="background-color: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Verify Email</a></p>
                <p>Or copy and paste this link into your browser:</p>
                <p style="word-break: break-all;">%s</p>
                <p>This link will expire in 24 hours.</p>
                <p>If you didn't create an account, you can safely ignore this email.</p>
            </body>
            </html>
            """.formatted(verificationLink, verificationLink);
    }
}
