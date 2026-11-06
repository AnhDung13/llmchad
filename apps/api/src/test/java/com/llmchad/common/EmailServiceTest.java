package com.llmchad.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.llmchad.support.TestAssertions.assertAppException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** Dựng mail thật (không gửi): JavaMailSender là mock, chỉ bắt MimeMessage lại để đọc nội dung. */
class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        emailService = new EmailService(mailSender, "LLM Chad <noreply@example.com>",
                "https://llmchad.site/apple-touch-icon.png");
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    /** Toàn bộ mail dạng MIME thô (header + các phần), đã giải mã quoted-printable đủ để tìm chuỗi */
    private String sentRaw() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void sendVerificationCode_vietnamese_htmlWithLinkedLogoAndNoAttachment() throws Exception {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));

        emailService.sendVerificationCode("an@example.com", "a7Kp2Q", Duration.ofMinutes(15));

        String raw = sentRaw();
        assertThat(raw).contains("To: an@example.com")
                .contains("text/html")
                .contains("text/plain")
                .contains("https://llmchad.site/apple-touch-icon.png")
                .contains("a7Kp2Q")
                // Không đính kèm ảnh nào → Gmail không hiện chip tệp đính kèm
                .doesNotContain("image/png")
                .doesNotContain("Content-Disposition: attachment")
                .doesNotContain("{{");
    }

    @Test
    void sendVerificationCode_englishLocale_usesEnglishSubject() throws Exception {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        emailService.sendVerificationCode("an@example.com", "a7Kp2Q", Duration.ofMinutes(15));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Verify your email for LLM Chad");
    }

    @Test
    void sendVerificationCode_smtpFails_throwsBadGateway() {
        doThrow(new MailSendException("535 Username and Password not accepted"))
                .when(mailSender).send(any(MimeMessage.class));

        assertAppException(() -> emailService.sendVerificationCode("an@example.com", "a7Kp2Q", Duration.ofMinutes(15)),
                HttpStatus.BAD_GATEWAY, MessageKey.SEND_MAIL_FAILED);
    }
}
