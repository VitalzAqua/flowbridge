package com.flowbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.dto.CoreBankingResponse;
import com.flowbridge.entity.AuditLogEntity;
import com.flowbridge.entity.ExternalSystemResponseEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.ExternalSystemStatus;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import com.flowbridge.kafka.WorkflowEvent;
import com.flowbridge.kafka.WorkflowEventProducer;
import com.flowbridge.repository.AuditLogRepository;
import com.flowbridge.repository.ExternalSystemResponseRepository;
import com.flowbridge.repository.WorkflowRequestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowProcessingServiceTest {

    private final WorkflowRequestRepository workflowRequestRepository = mock(WorkflowRequestRepository.class);
    private final ExternalSystemResponseRepository externalSystemResponseRepository =
            mock(ExternalSystemResponseRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogService auditLogService = new AuditLogService(auditLogRepository);
    private final MockCoreBankingService mockCoreBankingService = mock(MockCoreBankingService.class);
    private final WorkflowStatusTransitionValidator statusTransitionValidator = new WorkflowStatusTransitionValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final WorkflowProcessingService workflowProcessingService = new WorkflowProcessingService(
            workflowRequestRepository,
            externalSystemResponseRepository,
            auditLogService,
            mockCoreBankingService,
            statusTransitionValidator,
            objectMapper
    );

    @Test
    void processesMappedWorkflowSuccessfully() {
        WorkflowRequestEntity workflowRequest = mappedWorkflow("""
                {
                  "customer_id": "C123",
                  "customer_name": "Alice Chen",
                  "product_code": "SAV001",
                  "advisor_id": "ADV001"
                }
                """);
        WorkflowEvent event = accountOpeningMappedEvent();
        CoreBankingResponse coreBankingResponse = new CoreBankingResponse(
                "CORE-123",
                ExternalSystemStatus.SUCCESS,
                null,
                "Account created successfully"
        );

        when(workflowRequestRepository.findById(10L)).thenReturn(Optional.of(workflowRequest));
        when(workflowRequestRepository.save(any(WorkflowRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockCoreBankingService.openAccount(any())).thenReturn(coreBankingResponse);

        workflowProcessingService.processWorkflowEvent(event);

        assertThat(workflowRequest.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(workflowRequest.getFailureReason()).isNull();

        verify(workflowRequestRepository, times(2)).save(workflowRequest);

        ArgumentCaptor<ExternalSystemResponseEntity> responseCaptor =
                ArgumentCaptor.forClass(ExternalSystemResponseEntity.class);
        verify(externalSystemResponseRepository).save(responseCaptor.capture());
        ExternalSystemResponseEntity savedExternalResponse = responseCaptor.getValue();
        assertThat(savedExternalResponse.getWorkflowRequest()).isSameAs(workflowRequest);
        assertThat(savedExternalResponse.getExternalReferenceId()).isEqualTo("CORE-123");
        assertThat(savedExternalResponse.getStatus()).isEqualTo(ExternalSystemStatus.SUCCESS);

        ArgumentCaptor<AuditLogEntity> auditLogCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository, times(4)).save(auditLogCaptor.capture());
        List<AuditLogEntity> auditLogs = auditLogCaptor.getAllValues();

        assertThat(auditLogs)
                .extracting(AuditLogEntity::getEventType)
                .containsExactly(
                        AuditEventType.PROCESSING_STARTED,
                        AuditEventType.EXTERNAL_SYSTEM_CALL_STARTED,
                        AuditEventType.EXTERNAL_SYSTEM_CALL_SUCCEEDED,
                        AuditEventType.WORKFLOW_COMPLETED
                );
        assertThat(auditLogs.getLast().getMetadata()).contains("CORE-123");
    }

    @Test
    void marksWorkflowFailedWhenCoreBankingRejectsRequest() {
        WorkflowRequestEntity workflowRequest = mappedWorkflow("""
                {
                  "customer_id": "FAIL-123",
                  "customer_name": "Alice Chen",
                  "product_code": "SAV001",
                  "advisor_id": "ADV001"
                }
                """);
        WorkflowEvent event = accountOpeningMappedEvent();
        CoreBankingResponse coreBankingResponse = new CoreBankingResponse(
                null,
                ExternalSystemStatus.FAILED,
                "CORE_BANKING_REJECTED",
                "Core banking rejected the account-opening request"
        );

        when(workflowRequestRepository.findById(10L)).thenReturn(Optional.of(workflowRequest));
        when(workflowRequestRepository.save(any(WorkflowRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockCoreBankingService.openAccount(any())).thenReturn(coreBankingResponse);

        workflowProcessingService.processWorkflowEvent(event);

        assertThat(workflowRequest.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(workflowRequest.getFailureReason())
                .isEqualTo("Core banking rejected the account-opening request");

        ArgumentCaptor<ExternalSystemResponseEntity> responseCaptor =
                ArgumentCaptor.forClass(ExternalSystemResponseEntity.class);
        verify(externalSystemResponseRepository).save(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getStatus()).isEqualTo(ExternalSystemStatus.FAILED);
        assertThat(responseCaptor.getValue().getErrorCode()).isEqualTo("CORE_BANKING_REJECTED");

        ArgumentCaptor<AuditLogEntity> auditLogCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository, times(4)).save(auditLogCaptor.capture());
        assertThat(auditLogCaptor.getAllValues())
                .extracting(AuditLogEntity::getEventType)
                .containsExactly(
                        AuditEventType.PROCESSING_STARTED,
                        AuditEventType.EXTERNAL_SYSTEM_CALL_STARTED,
                        AuditEventType.EXTERNAL_SYSTEM_CALL_FAILED,
                        AuditEventType.WORKFLOW_FAILED
                );
    }

    @Test
    void skipsDuplicateEventForCompletedWorkflow() {
        WorkflowRequestEntity workflowRequest = mappedWorkflow("""
                {
                  "customer_id": "C123",
                  "product_code": "SAV001"
                }
                """);
        workflowRequest.setStatus(WorkflowStatus.COMPLETED);

        when(workflowRequestRepository.findById(10L)).thenReturn(Optional.of(workflowRequest));

        workflowProcessingService.processWorkflowEvent(accountOpeningMappedEvent());

        verify(workflowRequestRepository, never()).save(any(WorkflowRequestEntity.class));
        verify(mockCoreBankingService, never()).openAccount(any());
        verify(externalSystemResponseRepository, never()).save(any(ExternalSystemResponseEntity.class));

        ArgumentCaptor<AuditLogEntity> auditLogCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());
        assertThat(auditLogCaptor.getValue().getEventType()).isEqualTo(AuditEventType.DUPLICATE_EVENT_SKIPPED);
    }

    @Test
    void skipsDuplicateEventWhenSuccessfulExternalResponseAlreadyExists() {
        WorkflowRequestEntity workflowRequest = mappedWorkflow("""
                {
                  "customer_id": "C123",
                  "product_code": "SAV001"
                }
                """);

        when(workflowRequestRepository.findById(10L)).thenReturn(Optional.of(workflowRequest));
        when(externalSystemResponseRepository.existsByWorkflowRequest_IdAndStatus(
                10L,
                ExternalSystemStatus.SUCCESS
        )).thenReturn(true);

        workflowProcessingService.processWorkflowEvent(accountOpeningMappedEvent());

        verify(workflowRequestRepository, never()).save(any(WorkflowRequestEntity.class));
        verify(mockCoreBankingService, never()).openAccount(any());
        verify(externalSystemResponseRepository, never()).save(any(ExternalSystemResponseEntity.class));

        ArgumentCaptor<AuditLogEntity> auditLogCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());
        assertThat(auditLogCaptor.getValue().getEventType()).isEqualTo(AuditEventType.DUPLICATE_EVENT_SKIPPED);
        assertThat(auditLogCaptor.getValue().getMessage())
                .isEqualTo("Skipped duplicate Kafka event because workflow was already processed successfully");
    }

    @Test
    void ignoresUnsupportedEventTypes() {
        WorkflowEvent unsupportedEvent = new WorkflowEvent(
                10L,
                WorkflowType.ACCOUNT_OPENING,
                "UNSUPPORTED_EVENT",
                "corr-123",
                Instant.parse("2026-05-27T10:00:00Z")
        );

        workflowProcessingService.processWorkflowEvent(unsupportedEvent);

        verify(workflowRequestRepository, never()).findById(any());
        verify(mockCoreBankingService, never()).openAccount(any());
    }

    private WorkflowRequestEntity mappedWorkflow(String mappedPayload) {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.MAPPED);
        workflowRequest.setCorrelationId("corr-123");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "C123"
                }
                """);
        workflowRequest.setMappedPayload(mappedPayload);
        return workflowRequest;
    }

    private WorkflowEvent accountOpeningMappedEvent() {
        return new WorkflowEvent(
                10L,
                WorkflowType.ACCOUNT_OPENING,
                WorkflowEventProducer.ACCOUNT_OPENING_MAPPED_EVENT,
                "corr-123",
                Instant.parse("2026-05-27T10:00:00Z")
        );
    }
}
