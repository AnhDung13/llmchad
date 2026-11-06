package com.llmchad.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.llmchad.exception.AppException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private record VerificationTexts(String subject, String preheader, String title, String intro, String ignore,
            String expires) {
    }

    private static final VerificationTexts VI = new VerificationTexts(
            "Xác minh email của bạn trên LLM Chad",
            "Mã xác minh của bạn là %s, có hiệu lực trong %d phút.",
            "Mã xác minh của bạn",
            "Bạn nhận được email này vì vừa đăng ký tài khoản LLM Chad. Nhập mã bên dưới để hoàn tất.",
            "Nếu bạn không đăng ký, hãy bỏ qua email này.",
            "Mã có hiệu lực trong %d phút.");

    private static final VerificationTexts EN = new VerificationTexts(
            "Verify your email for LLM Chad",
            "Your verification code is %s. It expires in %d minutes.",
            "Your verification code",
            "You're receiving this email because you just signed up for LLM Chad. Enter the code below to finish.",
            "If you didn't sign up, you can safely ignore this email.",
            "The code expires in %d minutes.");

    private final JavaMailSender javaMailSender;
    private final String from;
    private final String verificationTemplate;
    private final String logoUrl;

    public EmailService(JavaMailSender javaMailSender, @Value("${app.mail.from}") String from,
            @Value("${app.mail.logo-url}") String logoUrl) {
        this.javaMailSender = javaMailSender;
        this.from = from;
        this.logoUrl = logoUrl;
        this.verificationTemplate = readResource("mail/verification-code.html");
    }

    public void sendVerificationCode(String to, String code, Duration validFor) {
        boolean english = "en".equals(LocaleContextHolder.getLocale().getLanguage());
        VerificationTexts texts = english ? EN : VI;
        String expires = texts.expires().formatted(validFor.toMinutes());

        String html = fill(verificationTemplate, Map.of(
                "lang", english ? "en" : "vi",
                "title", texts.title(),
                "preheader", texts.preheader().formatted(code, validFor.toMinutes()),
                "logoUrl", logoUrl,
                "intro", texts.intro(),
                "code", code,
                "ignore", texts.ignore(),
                "expires", expires,
                "footer", "© " + Year.now().getValue() + " LLM Chad"));
        String plainText = "%s: %s\n\n%s %s".formatted(texts.title(), code, texts.ignore(), expires);

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(texts.subject());
            helper.setText(plainText, html);
            javaMailSender.send(message);
        } catch (MailException | MessagingException e) {
            log.error("Gửi mail xác minh tới {} thất bại", to, e);
            throw new AppException(HttpStatus.BAD_GATEWAY, MessageKey.SEND_MAIL_FAILED);
        }
    }

    private static String fill(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private static String readResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được " + path, e);
        }
    }
}
