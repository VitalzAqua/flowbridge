package com.flowbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.dto.AccountOpeningRequest;
import com.flowbridge.dto.WorkflowResponse;
import com.flowbridge.entity.AuditLogEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AccountType;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import com.flowbridge.repository.AuditLogRepository;
import com.flowbridge.repository.WorkflowRequestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class aWorkflowServiceTest {

    private final WorkflowRequestRepository workflowRequestRepository = mock(WorkflowRequestRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final CorrelationIdService correlationIdService = mock(CorrelationIdService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final WorkflowService workflowService = new WorkflowService(
            workflowRequestRepository,
            auditLogRepository,
            correlationIdService,
            objectMapper
    );

    @Test
    void createsAndValidatesAccountOpeningWorkflow() {
        AccountOpeningRequest request = new AccountOpeningRequest();
        request.setClientId("C123");
        request.setFullName("Alice Chen");
        request.setDateOfBirth(LocalDate.of(2001, 5, 1));
        request.setAccountType(AccountType.SAVINGS);
        request.setAdvisorCode("ADV001");

        when(correlationIdService.generateCorrelationId()).thenReturn("corr-123");
        when(workflowRequestRepository.save(any(WorkflowRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowResponse response = workflowService.createAccountOpeningWorkflow(request);

        ArgumentCaptor<WorkflowRequestEntity> workflowCaptor =
                ArgumentCaptor.forClass(WorkflowRequestEntity.class);
        verify(workflowRequestRepository, times(2)).save(workflowCaptor.capture());
        List<WorkflowRequestEntity> savedWorkflows = workflowCaptor.getAllValues();
        WorkflowRequestEntity receivedWorkflow = savedWorkflows.getFirst();
        WorkflowRequestEntity validatedWorkflow = savedWorkflows.getLast();

        assertThat(receivedWorkflow.getWorkflowType()).isEqualTo(WorkflowType.ACCOUNT_OPENING);
        assertThat(receivedWorkflow.getSourceSystem()).isEqualTo("DIGITAL_CHANNEL");
        assertThat(receivedWorkflow.getCorrelationId()).isEqualTo("corr-123");
        assertThat(receivedWorkflow.getOriginalPayload()).contains("Alice Chen");
        assertThat(receivedWorkflow.getOriginalPayload()).contains("SAVINGS");
        assertThat(validatedWorkflow.getStatus()).isEqualTo(WorkflowStatus.VALIDATED);
        assertThat(validatedWorkflow.getFailureReason()).isNull();

        ArgumentCaptor<AuditLogEntity> auditLogCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository, times(2)).save(auditLogCaptor.capture());
        List<AuditLogEntity> savedAuditLogs = auditLogCaptor.getAllValues();

        assertThat(savedAuditLogs.getFirst().getWorkflowRequest()).isSameAs(validatedWorkflow);
        assertThat(savedAuditLogs.getFirst().getCorrelationId()).isEqualTo("corr-123");
        assertThat(savedAuditLogs.getFirst().getEventType()).isEqualTo(AuditEventType.REQUEST_RECEIVED);
        assertThat(savedAuditLogs.getFirst().getMessage()).isEqualTo("Account-opening request was received");
        assertThat(savedAuditLogs.getFirst().getMetadata()).contains("DIGITAL_CHANNEL");
        assertThat(savedAuditLogs.getLast().getWorkflowRequest()).isSameAs(validatedWorkflow);
        assertThat(savedAuditLogs.getLast().getCorrelationId()).isEqualTo("corr-123");
        assertThat(savedAuditLogs.getLast().getEventType()).isEqualTo(AuditEventType.VALIDATION_PASSED);
        assertThat(savedAuditLogs.getLast().getMessage()).isEqualTo("Account-opening request passed validation");

        assertThat(response.getWorkflowType()).isEqualTo(WorkflowType.ACCOUNT_OPENING);
        assertThat(response.getStatus()).isEqualTo(WorkflowStatus.VALIDATED);
        assertThat(response.getCorrelationId()).isEqualTo("corr-123");
        assertThat(response.getMessage()).isEqualTo("Account opening workflow validated successfully");
    }

    @Test
    void failsAccountOpeningWorkflowWhenClientIsUnderage() {
        AccountOpeningRequest request = new AccountOpeningRequest();
        request.setClientId("C456");
        request.setFullName("Jordan Lee");
        request.setDateOfBirth(LocalDate.now().minusYears(17));
        request.setAccountType(AccountType.TFSA);

        when(correlationIdService.generateCorrelationId()).thenReturn("corr-456");
        when(workflowRequestRepository.save(any(WorkflowRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowResponse response = workflowService.createAccountOpeningWorkflow(request);

        ArgumentCaptor<WorkflowRequestEntity> workflowCaptor =
                ArgumentCaptor.forClass(WorkflowRequestEntity.class);
        verify(workflowRequestRepository, times(2)).save(workflowCaptor.capture());
        WorkflowRequestEntity failedWorkflow = workflowCaptor.getAllValues().getLast();

        assertThat(failedWorkflow.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(failedWorkflow.getFailureReason()).isEqualTo("client must be at least 18 years old");

        ArgumentCaptor<AuditLogEntity> auditLogCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository, times(2)).save(auditLogCaptor.capture());
        AuditLogEntity validationAuditLog = auditLogCaptor.getAllValues().getLast();

        assertThat(validationAuditLog.getCorrelationId()).isEqualTo("corr-456");
        assertThat(validationAuditLog.getEventType()).isEqualTo(AuditEventType.VALIDATION_FAILED);
        assertThat(validationAuditLog.getMessage()).isEqualTo("Account-opening request failed validation");
        assertThat(validationAuditLog.getMetadata()).contains("client must be at least 18 years old");

        assertThat(response.getWorkflowType()).isEqualTo(WorkflowType.ACCOUNT_OPENING);
        assertThat(response.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(response.getCorrelationId()).isEqualTo("corr-456");
        assertThat(response.getMessage()).isEqualTo("Account opening workflow failed validation");
    }
}
