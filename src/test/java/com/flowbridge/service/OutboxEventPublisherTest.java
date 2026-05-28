package com.flowbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.entity.AuditLogEntity;
import com.flowbridge.entity.OutboxEventEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.OutboxEventStatus;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import com.flowbridge.kafka.WorkflowEvent;
import com.flowbridge.kafka.WorkflowEventProducer;
import com.flowbridge.repository.AuditLogRepository;
import com.flowbridge.repository.OutboxEventRepository;
import com.flowbridge.repository.WorkflowRequestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventPublisherTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final WorkflowRequestRepository workflowRequestRepository = mock(WorkflowRequestRepository.class);
    private final WorkflowEventProducer workflowEventProducer = mock(WorkflowEventProducer.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogService auditLogService = new AuditLogService(auditLogRepository);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final OutboxEventPublisher outboxEventPublisher = new OutboxEventPublisher(
            outboxEventRepository,
            workflowRequestRepository,
            workflowEventProducer,
            auditLogService,
            objectMapper
    );

    @Test
    void publishesPendingOutboxEventAndWritesAuditLog() throws Exception {
        WorkflowRequestEntity workflowRequest = workflowRequest();
        WorkflowEvent workflowEvent = new WorkflowEvent(
                10L,
                WorkflowType.ACCOUNT_OPENING,
                WorkflowEventProducer.ACCOUNT_OPENING_MAPPED_EVENT,
                "corr-123",
                "ACCOUNT_OPENING:corr-123",
                Instant.parse("2026-05-28T10:00:00Z")
        );
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        ReflectionTestUtils.setField(outboxEvent, "id", 100L);
        outboxEvent.setWorkflowRequest(workflowRequest);
        outboxEvent.setEventType(WorkflowEventProducer.ACCOUNT_OPENING_MAPPED_EVENT);
        outboxEvent.setIdempotencyKey(workflowEvent.getIdempotencyKey());
        outboxEvent.setPayload(objectMapper.writeValueAsString(workflowEvent));
        outboxEvent.setStatus(OutboxEventStatus.PENDING);

        when(outboxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEvent));
        when(workflowRequestRepository.findById(10L)).thenReturn(Optional.of(workflowRequest));
        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int publishedCount = outboxEventPublisher.publishPendingEvents();

        assertThat(publishedCount).isEqualTo(1);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEvent.getPublishedAt()).isNotNull();
        assertThat(outboxEvent.getFailureReason()).isNull();

        verify(workflowEventProducer).publishEvent(any(WorkflowEvent.class));

        ArgumentCaptor<AuditLogEntity> auditLogCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());
        assertThat(auditLogCaptor.getValue().getEventType()).isEqualTo(AuditEventType.KAFKA_EVENT_PUBLISHED);
        assertThat(auditLogCaptor.getValue().getMetadata()).contains("ACCOUNT_OPENING:corr-123");
    }

    private WorkflowRequestEntity workflowRequest() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        ReflectionTestUtils.setField(workflowRequest, "id", 10L);
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.MAPPED);
        workflowRequest.setCorrelationId("corr-123");
        workflowRequest.setIdempotencyKey("ACCOUNT_OPENING:corr-123");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "C123"
                }
                """);
        return workflowRequest;
    }
}
