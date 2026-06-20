package com.systeam.notificaciones.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, "noreply@ideafy.lat");
    }

    @Test
    @DisplayName("sendEmail exitoso — verifica que se llame a mailSender.send")
    void sendEmail_exitoso() throws Exception {
        MimeMessage mime = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mime);

        emailService.sendEmail("a@b.com", "Subject", "Body");

        verify(mailSender).send(mime);
    }

    @Test
    @DisplayName("sendEmail cuando falla — no lanza excepcion (fire-and-forget)")
    void sendEmail_cuandoFalla_noLanzaExcepcion() throws Exception {
        MimeMessage mime = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mime);
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(mime);

        // No exception thrown — fire-and-forget
        emailService.sendEmail("a@b.com", "Subject", "Body");

        verify(mailSender).send(mime);
    }
}
