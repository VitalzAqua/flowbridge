package com.flowbridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.entity.OutboxEventEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.OutboxEventStatus;
import com.flowbridge.exception.WorkflowNotFoundException;
import com.flowbridge.kafka.WorkflowEvent;
import com.flowbridge.kafka.WorkflowEventProducer;
import com.flowbridge.repository.OutboxEventRepository;
import com.flowbridge.repository.WorkflowRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final WorkflowRequestRepository workflowRequestRepository;
    private final WorkflowEventProducer workflowEventProducer;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            WorkflowRequestRepository workflowRequestRepository,
            WorkflowEventProducer workflowEventProducer,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.workflowRequestRepository = workflowRequestRepository;
        this.workflowEventProducer = workflowEventProducer;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int publishPendingEvents() {
        List<OutboxEventEntity> pendingEvents =
                outboxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        int publishedCount = 0;
        for (OutboxEventEntity outboxEvent : pendingEvents) {
            if (publishEvent(outboxEvent)) {
                publishedCount++;
            }
        }

        return publishedCount;
    }

    private boolean publishEvent(OutboxEventEntity outboxEvent) {
        try {
            WorkflowEvent workflowEvent = readWorkflowEvent(outboxEvent);
            workflowEventProducer.publishEvent(workflowEvent);

            outboxEvent.setStatus(OutboxEventStatus.PUBLISHED);
            outboxEvent.setPublishedAt(LocalDateTime.now());
            outboxEvent.setFailureReason(null);
            outboxEventRepository.save(outboxEvent);

            WorkflowRequestEntity workflowRequest = workflowRequestRepository.findById(workflowEvent.getWorkflowId())
                    .orElseThrow(() -> new WorkflowNotFoundException(workflowEvent.getWorkflowId()));
            saveKafkaEventPublishedAuditLog(workflowRequest, workflowEvent);
            return true;
        } catch (RuntimeException exception) {
            int nextRetryCount = outboxEvent.getRetryCount() + 1;
            outboxEvent.setRetryCount(nextRetryCount);
            outboxEvent.setStatus(OutboxEventStatus.FAILED);
            outboxEvent.setFailureReason(exception.getMessage());
            outboxEventRepository.save(outboxEvent);
            log.error(
                    "Failed to publish outbox event {} for workflow {} with idempotencyKey {}",
                    outboxEvent.getId(),
                    outboxEvent.getWorkflowRequest().getId(),
                    outboxEvent.getIdempotencyKey(),
                    exception
            );
            return false;
        }
    }

    private WorkflowEvent readWorkflowEvent(OutboxEventEntity outboxEvent) {
        try {
            return objectMapper.readValue(outboxEvent.getPayload(), WorkflowEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize outbox event " + outboxEvent.getId(), exception);
        }
    }

    private void saveKafkaEventPublishedAuditLog(WorkflowRequestEntity workflowRequest, WorkflowEvent workflowEvent) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.KAFKA_EVENT_PUBLISHED,
                "Published account-opening mapped event to Kafka",
                toJson(new KafkaEventPublishedMetadata(
                        workflowEvent.getEventType(),
                        workflowEvent.getIdempotencyKey(),
                        workflowEvent.getTimestamp().toString()
                ))
        );
    }

    private String toJson(Object metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Kafka event metadata", exception);
        }
    }

    private record KafkaEventPublishedMetadata(String eventType, String idempotencyKey, String timestamp) {
    }
}
