package com.flowbridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.dto.AccountOpeningRequest;
import com.flowbridge.dto.WorkflowResponse;
import com.flowbridge.entity.AuditLogEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import com.flowbridge.repository.AuditLogRepository;
import com.flowbridge.repository.WorkflowRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkflowService {

    private static final String DIGITAL_CHANNEL_SOURCE_SYSTEM = "DIGITAL_CHANNEL";
    private static final int MINIMUM_ACCOUNT_OPENING_AGE = 18;

    private final WorkflowRequestRepository workflowRequestRepository;
    private final AuditLogRepository auditLogRepository;
    private final CorrelationIdService correlationIdService;
    private final ObjectMapper objectMapper;

    public WorkflowService(
            WorkflowRequestRepository workflowRequestRepository,
            AuditLogRepository auditLogRepository,
            CorrelationIdService correlationIdService,
            ObjectMapper objectMapper
    ) {
        this.workflowRequestRepository = workflowRequestRepository;
        this.auditLogRepository = auditLogRepository;
        this.correlationIdService = correlationIdService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowResponse createAccountOpeningWorkflow(AccountOpeningRequest request) {
        String correlationId = correlationIdService.generateCorrelationId();

        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem(DIGITAL_CHANNEL_SOURCE_SYSTEM);
        workflowRequest.setStatus(WorkflowStatus.RECEIVED);
        workflowRequest.setCorrelationId(correlationId);
        workflowRequest.setOriginalPayload(toJson(request));

        WorkflowRequestEntity savedWorkflow = workflowRequestRepository.save(workflowRequest);
        saveRequestReceivedAuditLog(savedWorkflow);

        List<String> validationErrors = validateAccountOpeningRequest(request);
        if (validationErrors.isEmpty()) {
            savedWorkflow.setStatus(WorkflowStatus.VALIDATED);
            WorkflowRequestEntity validatedWorkflow = workflowRequestRepository.save(savedWorkflow);
            saveValidationPassedAuditLog(validatedWorkflow);

            return new WorkflowResponse(
                    validatedWorkflow.getId(),
                    validatedWorkflow.getWorkflowType(),
                    validatedWorkflow.getStatus(),
                    validatedWorkflow.getCorrelationId(),
                    "Account opening workflow validated successfully"
            );
        }

        String failureReason = String.join("; ", validationErrors);
        savedWorkflow.setStatus(WorkflowStatus.FAILED);
        savedWorkflow.setFailureReason(failureReason);
        WorkflowRequestEntity failedWorkflow = workflowRequestRepository.save(savedWorkflow);
        saveValidationFailedAuditLog(failedWorkflow, failureReason);

        return new WorkflowResponse(
                failedWorkflow.getId(),
                failedWorkflow.getWorkflowType(),
                failedWorkflow.getStatus(),
                failedWorkflow.getCorrelationId(),
                "Account opening workflow failed validation"
        );
    }

    private void saveRequestReceivedAuditLog(WorkflowRequestEntity workflowRequest) {
        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setWorkflowRequest(workflowRequest);
        auditLog.setCorrelationId(workflowRequest.getCorrelationId());
        auditLog.setEventType(AuditEventType.REQUEST_RECEIVED);
        auditLog.setMessage("Account-opening request was received");
        auditLog.setMetadata("""
                {
                  "sourceSystem": "DIGITAL_CHANNEL"
                }
                """);

        auditLogRepository.save(auditLog);
    }

    private void saveValidationPassedAuditLog(WorkflowRequestEntity workflowRequest) {
        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setWorkflowRequest(workflowRequest);
        auditLog.setCorrelationId(workflowRequest.getCorrelationId());
        auditLog.setEventType(AuditEventType.VALIDATION_PASSED);
        auditLog.setMessage("Account-opening request passed validation");
        auditLog.setMetadata("""
                {
                  "minimumAge": 18
                }
                """);

        auditLogRepository.save(auditLog);
    }

    private void saveValidationFailedAuditLog(WorkflowRequestEntity workflowRequest, String failureReason) {
        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setWorkflowRequest(workflowRequest);
        auditLog.setCorrelationId(workflowRequest.getCorrelationId());
        auditLog.setEventType(AuditEventType.VALIDATION_FAILED);
        auditLog.setMessage("Account-opening request failed validation");
        auditLog.setMetadata(toJson(new ValidationFailureMetadata(failureReason)));

        auditLogRepository.save(auditLog);
    }

    private List<String> validateAccountOpeningRequest(AccountOpeningRequest request) {
        List<String> validationErrors = new ArrayList<>();

        LocalDate dateOfBirth = request.getDateOfBirth();
        if (dateOfBirth == null) {
            validationErrors.add("dateOfBirth is required");
            return validationErrors;
        }

        LocalDate today = LocalDate.now();
        if (dateOfBirth.isAfter(today)) {
            validationErrors.add("dateOfBirth cannot be in the future");
        } else if (Period.between(dateOfBirth, today).getYears() < MINIMUM_ACCOUNT_OPENING_AGE) {
            validationErrors.add("client must be at least 18 years old");
        }

        return validationErrors;
    }

    private String toJson(AccountOpeningRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize account-opening request", exception);
        }
    }

    private String toJson(ValidationFailureMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize validation failure metadata", exception);
        }
    }

    private record ValidationFailureMetadata(String failureReason) {
    }
}
