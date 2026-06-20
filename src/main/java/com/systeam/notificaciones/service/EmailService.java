package com.systeam.notificaciones.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender,
                        @Value("${mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Async("emailTaskExecutor")
    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(buildHtmlTemplate(subject, body), true);
            mailSender.send(message);
            log.info("Email enviado a {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Error enviando email a {} (subject: {}): {}", to, subject, e.getMessage());
        }
    }

    private String buildHtmlTemplate(String title, String body) {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;background-color:#0A0C14;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0A0C14;padding:40px 16px;">
                    <tr>
                        <td align="center">
                            <!-- Brand header -->
                            <table role="presentation" width="100%%" style="max-width:520px;">
                                <tr>
                                    <td align="center" style="padding-bottom:24px;">
                                        <span style="font-size:22px;font-weight:800;color:#7C3AED;letter-spacing:-0.5px;">IDEAFY</span>
                                    </td>
                                </tr>
                            </table>

                            <!-- Card -->
                            <table role="presentation" width="100%%" style="max-width:520px;background-color:#13172B;border:1px solid #1E2240;border-radius:10px;padding:32px;">
                                <tr>
                                    <td>
                                        <!-- Icon / accent line -->
                                        <table role="presentation" width="40" cellpadding="0" cellspacing="0" style="margin-bottom:20px;">
                                            <tr><td style="height:4px;background-color:#7C3AED;border-radius:2px;"></td></tr>
                                        </table>

                                        <!-- Title -->
                                        <h1 style="margin:0 0 8px 0;font-size:18px;font-weight:600;color:#F1F5F9;letter-spacing:-0.2px;">
                                            %s
                                        </h1>

                                        <!-- Body -->
                                        <p style="margin:0 0 24px 0;font-size:14px;line-height:1.6;color:#94A3B8;">
                                            %s
                                        </p>

                                        <!-- Divider -->
                                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:16px;">
                                            <tr><td style="height:1px;background-color:#1E2240;"></td></tr>
                                        </table>

                                        <!-- Footer -->
                                        <p style="margin:0;font-size:12px;color:#64748B;">
                                            &copy; 2026 IDEAFY &mdash; Plataforma de inversión
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(title, body.replace("\n", "<br>"));
    }
}
