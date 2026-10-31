package com.pulseclinic.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DevelopmentEmailWorker {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentEmailWorker.class);
    private final EmailOutboxRepository outbox;
    DevelopmentEmailWorker(EmailOutboxRepository outbox) { this.outbox = outbox; }
    @Scheduled(fixedDelayString = "${pulseclinic.notifications.poll-ms:15000}")
    void deliver() {
        for (EmailOutboxRepository.OutboxMessage message : outbox.pending()) {
            try { log.info("Development email delivered: template={} outboxId={}", message.template(), message.id()); outbox.sent(message.id()); }
            catch (RuntimeException ex) { outbox.failed(message.id(), "Development delivery failed"); }
        }
    }
}
