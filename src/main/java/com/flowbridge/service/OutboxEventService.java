package com.flowbridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.entity.OutboxEventEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.OutboxEventStatus;
import com.flowbridge.kafka.WorkflowEvent;
import com.flowbridge.kafka.WorkflowEventProducer;
import com.flowbridge.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public OutboxEventService(
            OutboxEventRepository outboxEventRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    public OutboxEventEntity queueAccountOpeningMappedEvent(WorkflowRequestEntity workflowRequest) {
        WorkflowEvent workflowEvent = new WorkflowEvent(
                workflowRequest.getId(),
                workflowRequest.getWorkflowType(),
                WorkflowEventProducer.ACCOUNT_OPENING_MAPPED_EVENT,
                workflowRequest.getCorrelationId(),
                workflowRequest.getIdempotencyKey(),
                Instant.now()
        );

        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setWorkflowRequest(workflowRequest);
        outboxEvent.setEventType(workflowEvent.getEventType());
        outboxEvent.setIdempotencyKey(workflowEvent.getIdempotencyKey());
        outboxEvent.setPayload(toJson(workflowEvent));
        outboxEvent.setStatus(OutboxEventStatus.PENDING);

        OutboxEventEntity savedOutboxEvent = outboxEventRepository.save(outboxEvent);
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.KAFKA_EVENT_QUEUED,
                "Queued account-opening mapped event in outbox",
                toJson(new KafkaEventQueuedMetadata(
                        savedOutboxEvent.getEventType(),
                        savedOutboxEvent.getIdempotencyKey()
                ))
        );

        return savedOutboxEvent;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize outbox event", exception);
        }
    }

    private record KafkaEventQueuedMetadata(String eventType, String idempotencyKey) {
    }
}
