package com.flowbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.dto.WorkflowResponse;
import com.flowbridge.entity.AuditLogEntity;
import com.flowbridge.entity.RetryAttemptEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.RetryAttemptStatus;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import com.flowbridge.exception.NonRetryableWorkflowException;
import com.flowbridge.kafka.WorkflowEvent;
import com.flowbridge.kafka.WorkflowEventProducer;
import com.flowbridge.repository.AuditLogRepository;
import com.flowbridge.repository.RetryAttemptRepository;
import com.flowbridge.repository.WorkflowRequestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryServiceTest {

    private final WorkflowRequestRepository workflowRequestRepository = mock(WorkflowRequestRepository.class);
    private final RetryAttemptRepository retryAttemptRepository = mock(RetryAttemptRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogService auditLogService = new AuditLogService(auditLogRepository);
    private final WorkflowStatusTransitionValidator statusTransitionValidator = new WorkflowStatusTransitionValidator();
    private final WorkflowEventProducer workflowEventProducer = mock(WorkflowEventProducer.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final RetryService retryService = new RetryService(
            workflowRequestRepository,
            retryAttemptRepository,
            auditLogService,
            statusTransitionValidator,
            workflowEventProducer,
            objectMapper
    );

    @Test
    void retriesFailedWorkflowAndRepublishesKafkaEvent() {
        WorkflowRequestEntity workflowRequest = failedMappedWorkflow(1);
        WorkflowEvent workflowEvent = new WorkflowEvent(
                10L,
                WorkflowType.ACCOUNT_OPENING,
                WorkflowEventProducer.ACCOUNT_OPENING_MAPPED_EVENT,
                "corr-123",
                Instant.parse("2026-05-27T10:00:00Z")
        );

        when(workflowRequestRepository.findById(10L)).thenReturn(Optional.of(workflowRequest));
        when(retryAttemptRepository.save(any(RetryAttemptEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowRequestRepository.save(any(WorkflowRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowEventProducer.publishAccountOpeningMappedEvent(workflowRequest)).thenReturn(workflowEvent);

        WorkflowResponse response = retryService.retryWorkflow(10L);

        assertThat(workflowRequest.getStatus()).isEqualTo(WorkflowStatus.RETRYING);
        assertThat(workflowRequest.getRetryCount()).isEqualTo(2);
        assertThat(workflowRequest.getFailureReason()).isNull();

        ArgumentCaptor<RetryAttemptEntity> retryAttemptCaptor =
                ArgumentCaptor.forClass(RetryAttemptEntity.class);
        verify(retryAttemptRepository, times(2)).save(retryAttemptCaptor.capture());
        List<RetryAttemptEntity> savedRetryAttempts = retryAttemptCaptor.getAllValues();
        assertThat(savedRetryAttempts.getFirst().getWorkflowRequest()).isSameAs(workflowRequest);
        assertThat(savedRetryAttempts.getFirst().getAttemptNumber()).isEqualTo(2);
        assertThat(savedRetryAttempts.getFirst().getFailureReason())
                .isEqualTo("Core banking rejected the account-opening request");
        assertThat(savedRetryAttempts.getLast().getStatus()).isEqualTo(RetryAttemptStatus.EVENT_PUBLISHED);

        verify(workflowRequestRepository).save(workflowRequest);
        verify(workflowEventProducer).publishAccountOpeningMappedEvent(workflowRequest);

        ArgumentCaptor<AuditLogEntity> auditLogCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository, times(2)).save(auditLogCaptor.capture());
        assertThat(auditLogCaptor.getAllValues())
                .extracting(AuditLogEntity::getEventType)
                .containsExactly(
                        AuditEventType.RETRY_REQUESTED,
                        AuditEventType.KAFKA_EVENT_PUBLISHED
                );
        assertThat(auditLogCaptor.getAllValues().getFirst().getMetadata()).contains("attemptNumber");

        assertThat(response.getWorkflowId()).isEqualTo(workflowRequest.getId());
        assertThat(response.getWorkflowType()).isEqualTo(WorkflowType.ACCOUNT_OPENING);
        assertThat(response.getStatus()).isEqualTo(WorkflowStatus.RETRYING);
        assertThat(response.getCorrelationId()).isEqualTo("corr-123");
    }

    @Test
    void rejectsRetryWhenWorkflowIsNotFailed() {
        WorkflowRequestEntity workflowRequest = failedMappedWorkflow(0);
        workflowRequest.setStatus(WorkflowStatus.COMPLETED);

        when(workflowRequestRepository.findById(10L)).thenReturn(Optional.of(workflowRequest));

        assertThatThrownBy(() -> retryService.retryWorkflow(10L))
                .isInstanceOf(NonRetryableWorkflowException.class)
                .hasMessageContaining("cannot be retried from status COMPLETED");

        verify(retryAttemptRepository, never()).save(any(RetryAttemptEntity.class));
        verify(workflowEventProducer, never()).publishAccountOpeningMappedEvent(any(WorkflowRequestEntity.class));
    }

    @Test
    void rejectsRetryWhenMaxRetryCountReached() {
        WorkflowRequestEntity workflowRequest = failedMappedWorkflow(3);

        when(workflowRequestRepository.findById(10L)).thenReturn(Optional.of(workflowRequest));

        assertThatThrownBy(() -> retryService.retryWorkflow(10L))
                .isInstanceOf(NonRetryableWorkflowException.class)
                .hasMessageContaining("has reached max retry count 3");

        verify(retryAttemptRepository, never()).save(any(RetryAttemptEntity.class));
        verify(workflowEventProducer, never()).publishAccountOpeningMappedEvent(any(WorkflowRequestEntity.class));
    }

    @Test
    void rejectsRetryWhenFailedWorkflowHasNoMappedPayload() {
        WorkflowRequestEntity workflowRequest = failedMappedWorkflow(0);
        workflowRequest.setMappedPayload(null);

        when(workflowRequestRepository.findById(10L)).thenReturn(Optional.of(workflowRequest));

        assertThatThrownBy(() -> retryService.retryWorkflow(10L))
                .isInstanceOf(NonRetryableWorkflowException.class)
                .hasMessageContaining("no mapped payload exists to reprocess");

        verify(retryAttemptRepository, never()).save(any(RetryAttemptEntity.class));
        verify(workflowEventProducer, never()).publishAccountOpeningMappedEvent(any(WorkflowRequestEntity.class));
    }

    private WorkflowRequestEntity failedMappedWorkflow(int retryCount) {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        ReflectionTestUtils.setField(workflowRequest, "id", 10L);
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.FAILED);
        workflowRequest.setCorrelationId("corr-123");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "FAIL-123"
                }
                """);
        workflowRequest.setMappedPayload("""
                {
                  "customer_id": "FAIL-123",
                  "customer_name": "Failure Test",
                  "product_code": "SAV001",
                  "advisor_id": "ADV001"
                }
                """);
        workflowRequest.setFailureReason("Core banking rejected the account-opening request");
        workflowRequest.setRetryCount(retryCount);
        return workflowRequest;
    }
}
