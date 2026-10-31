package com.pulseclinic.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseclinic.common.config.PulseClinicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class EmailOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxWorker.class);
    private final EmailOutboxRepository outbox;
    private final PulseClinicProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client = RestClient.create("https://api.resend.com");

    EmailOutboxWorker(EmailOutboxRepository outbox, PulseClinicProperties properties, ObjectMapper objectMapper) {
        this.outbox = outbox; this.properties = properties; this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${pulseclinic.notifications.poll-ms:15000}")
    void deliver() {
        for (EmailOutboxRepository.OutboxMessage message : outbox.pending()) {
            try {
                if (message.email().endsWith("@pulseclinic.demo")) {
                    log.info("Local email preview delivered: template={} outboxId={}", message.template(), message.id());
                } else {
                    sendWithResend(message);
                    log.info("Email delivered: template={} outboxId={}", message.template(), message.id());
                }
                outbox.sent(message.id());
            } catch (RuntimeException ex) {
                log.warn("Email delivery failed: template={} outboxId={}", message.template(), message.id());
                outbox.failed(message.id(), "Provider delivery failed");
            }
        }
    }

    private void sendWithResend(EmailOutboxRepository.OutboxMessage message) {
        String key = properties.getNotifications().getResendApiKey();
        if (key == null || key.isBlank()) throw new IllegalStateException("Email provider is not configured");
        JsonNode payload;
        try { payload = objectMapper.readTree(message.payload()); }
        catch (Exception ex) { throw new IllegalStateException("Invalid email payload"); }
        String link = payload.path("link").asText("");
        String publicLink = link.isBlank() ? "" : properties.getNotifications().getPublicUrl().replaceAll("/$", "") + link;
        EmailContent content = content(message.template(), publicLink);
        client.post().uri("/emails")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResendRequest(properties.getNotifications().getFrom(), message.email(), content.subject(), content.html()))
                .retrieve().toBodilessEntity();
    }

    private EmailContent content(String template, String link) {
        return switch (template) {
            case "PASSWORD_RESET" -> new EmailContent("Reset your PulseClinic password", "<p>Use the secure link below to choose a new PulseClinic password. It expires in one hour.</p><p><a href=\"" + link + "\">Reset password</a></p>");
            case "EMAIL_VERIFICATION" -> new EmailContent("Verify your PulseClinic email", "<p>Welcome to PulseClinic. Confirm your email address to finish setting up your account.</p><p><a href=\"" + link + "\">Verify email</a></p>");
            case "TEAM_INVITATION" -> new EmailContent("You are invited to PulseClinic", "<p>You have been invited to join PulseClinic. Use the secure link below to set your password. It expires in 24 hours.</p><p><a href=\"" + link + "\">Set up your account</a></p>");
            default -> new EmailContent("PulseClinic update", "<p>You have a new update from PulseClinic.</p>");
        };
    }
    private record ResendRequest(String from, String to, String subject, String html) {}
    private record EmailContent(String subject, String html) {}
}
