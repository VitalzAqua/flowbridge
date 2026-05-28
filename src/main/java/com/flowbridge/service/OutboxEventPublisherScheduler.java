package com.flowbridge.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "flowbridge.outbox.publisher.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxEventPublisherScheduler {

    private final OutboxEventPublisher outboxEventPublisher;

    public OutboxEventPublisherScheduler(OutboxEventPublisher outboxEventPublisher) {
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Scheduled(fixedDelayString = "${flowbridge.outbox.publisher.fixed-delay-ms:5000}")
    public void publishPendingEvents() {
        outboxEventPublisher.publishPendingEvents();
    }
}
