package com.flowbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.dto.AccountOpeningRequest;
import com.flowbridge.dto.AuditLogResponse;
import com.flowbridge.dto.WorkflowDetailResponse;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowServiceTest {

    private final WorkflowRequestRepository workflowRequestRepository = mock(WorkflowRequestRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditLogService auditLogService = new AuditLogService(auditLogRepository);
    private final CorrelationIdService correlationIdService = mock(CorrelationIdService.class);
    private final MappingService mappingService = new MappingService();
    private final WorkflowStatusTransitionValidator statusTransitionValidator = new WorkflowStatusTransitionValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final WorkflowService workflowService = new WorkflowService(
            workflowRequestRepository,
            auditLogService,
            correlationIdService,
            mappingService,
            statusTransitionValidator,
            objectMapper
    );

    @Test
    void createsValidatesAndMapsAccountOpeningWorkflow() {
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
        verify(workflowRequestRepository, times(3)).save(workflowCaptor.capture());
        List<WorkflowRequestEntity> savedWorkflows = workflowCaptor.getAllValues();
        WorkflowRequestEntity receivedWorkflow = savedWorkflows.getFirst();
        WorkflowRequestEntity mappedWorkflow = savedWorkflows.getLast();

        assertThat(receivedWorkflow.getWorkflowType()).isEqualTo(WorkflowType.ACCOUNT_OPENING);
        assertThat(receivedWorkflow.getSourceSystem()).isEqualTo("DIGITAL_CHANNEL");
        assertThat(receivedWorkflow.getCorrelationId()).isEqualTo("corr-123");
        assertThat(receivedWorkflow.getOriginalPayload()).contains("Alice Chen");
        assertThat(receivedWorkflow.getOriginalPayload()).contains("SAVINGS");
        assertThat(mappedWorkflow.getStatus()).isEqualTo(WorkflowStatus.MAPPED);
        assertThat(mappedWorkflow.getMappedPayload()).contains("customer_id");
        assertThat(mappedWorkflow.getMappedPayload()).contains("SAV001");
        assertThat(mappedWorkflow.getFailureReason()).isNull();

        ArgumentCaptor<AuditLogEntity> auditLogCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository, times(3)).save(auditLogCaptor.capture());
        List<AuditLogEntity> savedAuditLogs = auditLogCaptor.getAllValues();

        assertThat(savedAuditLogs.getFirst().getWorkflowRequest()).isSameAs(mappedWorkflow);
        assertThat(savedAuditLogs.getFirst().getCorrelationId()).isEqualTo("corr-123");
        assertThat(savedAuditLogs.getFirst().getEventType()).isEqualTo(AuditEventType.REQUEST_RECEIVED);
        assertThat(savedAuditLogs.getFirst().getMessage()).isEqualTo("Account-opening request was received");
        assertThat(savedAuditLogs.getFirst().getMetadata()).contains("DIGITAL_CHANNEL");
        assertThat(savedAuditLogs.get(1).getWorkflowRequest()).isSameAs(mappedWorkflow);
        assertThat(savedAuditLogs.get(1).getCorrelationId()).isEqualTo("corr-123");
        assertThat(savedAuditLogs.get(1).getEventType()).isEqualTo(AuditEventType.VALIDATION_PASSED);
        assertThat(savedAuditLogs.get(1).getMessage()).isEqualTo("Account-opening request passed validation");
        assertThat(savedAuditLogs.getLast().getWorkflowRequest()).isSameAs(mappedWorkflow);
        assertThat(savedAuditLogs.getLast().getCorrelationId()).isEqualTo("corr-123");
        assertThat(savedAuditLogs.getLast().getEventType()).isEqualTo(AuditEventType.PAYLOAD_MAPPED);
        assertThat(savedAuditLogs.getLast().getMessage()).isEqualTo("Mapped account-opening request to core banking payload");
        assertThat(savedAuditLogs.getLast().getMetadata()).contains("SAVINGS");
        assertThat(savedAuditLogs.getLast().getMetadata()).contains("SAV001");

        assertThat(response.getWorkflowType()).isEqualTo(WorkflowType.ACCOUNT_OPENING);
        assertThat(response.getStatus()).isEqualTo(WorkflowStatus.MAPPED);
        assertThat(response.getCorrelationId()).isEqualTo("corr-123");
        assertThat(response.getMessage()).isEqualTo("Account opening workflow received and mapped successfully");
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

    @Test
    void getsWorkflowDetailsById() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.MAPPED);
        workflowRequest.setCorrelationId("corr-789");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "C789"
                }
                """);
        workflowRequest.setMappedPayload("""
                {
                  "customer_id": "C789"
                }
                """);

        when(workflowRequestRepository.findById(10L)).thenReturn(Optional.of(workflowRequest));

        WorkflowDetailResponse response = workflowService.getWorkflow(10L);

        assertThat(response.getWorkflowType()).isEqualTo(WorkflowType.ACCOUNT_OPENING);
        assertThat(response.getSourceSystem()).isEqualTo("DIGITAL_CHANNEL");
        assertThat(response.getStatus()).isEqualTo(WorkflowStatus.MAPPED);
        assertThat(response.getCorrelationId()).isEqualTo("corr-789");
        assertThat(response.getOriginalPayload()).contains("C789");
        assertThat(response.getMappedPayload()).contains("customer_id");
    }

    @Test
    void getsAuditLogsForWorkflow() {
        when(workflowRequestRepository.existsById(10L)).thenReturn(true);
        when(auditLogRepository.findByWorkflowRequest_IdOrderByCreatedAtAsc(10L))
                .thenReturn(Collections.emptyList());

        List<AuditLogResponse> response = workflowService.getAuditLogs(10L);

        assertThat(response).isEmpty();
    }
}
