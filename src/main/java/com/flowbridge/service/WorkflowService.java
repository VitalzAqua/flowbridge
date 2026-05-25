package com.flowbridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.dto.AccountOpeningRequest;
import com.flowbridge.dto.AuditLogResponse;
import com.flowbridge.dto.CoreBankingPayload;
import com.flowbridge.dto.WorkflowDetailResponse;
import com.flowbridge.dto.WorkflowResponse;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import com.flowbridge.exception.WorkflowNotFoundException;
import com.flowbridge.repository.WorkflowRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private static final String DIGITAL_CHANNEL_SOURCE_SYSTEM = "DIGITAL_CHANNEL";
    private static final int MINIMUM_ACCOUNT_OPENING_AGE = 18;

    private final WorkflowRequestRepository workflowRequestRepository;
    private final AuditLogService auditLogService;
    private final CorrelationIdService correlationIdService;
    private final MappingService mappingService;
    private final WorkflowStatusTransitionValidator statusTransitionValidator;
    private final ObjectMapper objectMapper;

    public WorkflowService(
            WorkflowRequestRepository workflowRequestRepository,
            AuditLogService auditLogService,
            CorrelationIdService correlationIdService,
            MappingService mappingService,
            WorkflowStatusTransitionValidator statusTransitionValidator,
            ObjectMapper objectMapper
    ) {
        this.workflowRequestRepository = workflowRequestRepository;
        this.auditLogService = auditLogService;
        this.correlationIdService = correlationIdService;
        this.mappingService = mappingService;
        this.statusTransitionValidator = statusTransitionValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowResponse createAccountOpeningWorkflow(AccountOpeningRequest request) {
        String correlationId = correlationIdService.generateCorrelationId();
        log.info(
                "Starting account-opening workflow for clientId {} accountType {} correlationId {}",
                request.getClientId(),
                request.getAccountType(),
                correlationId
        );

        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem(DIGITAL_CHANNEL_SOURCE_SYSTEM);
        workflowRequest.setStatus(WorkflowStatus.RECEIVED);
        workflowRequest.setCorrelationId(correlationId);
        workflowRequest.setOriginalPayload(toJson(request));

        WorkflowRequestEntity savedWorkflow = workflowRequestRepository.save(workflowRequest);
        log.info(
                "Workflow {} with correlationId {} saved with status {}",
                savedWorkflow.getId(),
                savedWorkflow.getCorrelationId(),
                savedWorkflow.getStatus()
        );
        saveRequestReceivedAuditLog(savedWorkflow);

        List<String> validationErrors = validateAccountOpeningRequest(request);
        if (validationErrors.isEmpty()) {
            transitionWorkflow(savedWorkflow, WorkflowStatus.VALIDATED);
            WorkflowRequestEntity validatedWorkflow = workflowRequestRepository.save(savedWorkflow);
            saveValidationPassedAuditLog(validatedWorkflow);

            CoreBankingPayload coreBankingPayload = mappingService.mapAccountOpeningRequest(request);
            validatedWorkflow.setMappedPayload(toJson(coreBankingPayload));
            transitionWorkflow(validatedWorkflow, WorkflowStatus.MAPPED);
            WorkflowRequestEntity mappedWorkflow = workflowRequestRepository.save(validatedWorkflow);
            savePayloadMappedAuditLog(mappedWorkflow, request, coreBankingPayload);

            return new WorkflowResponse(
                    mappedWorkflow.getId(),
                    mappedWorkflow.getWorkflowType(),
                    mappedWorkflow.getStatus(),
                    mappedWorkflow.getCorrelationId(),
                    "Account opening workflow received and mapped successfully"
            );
        }

        String failureReason = String.join("; ", validationErrors);
        log.warn(
                "Workflow {} with correlationId {} failed validation: {}",
                savedWorkflow.getId(),
                savedWorkflow.getCorrelationId(),
                failureReason
        );
        transitionWorkflow(savedWorkflow, WorkflowStatus.FAILED);
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

    public WorkflowDetailResponse getWorkflow(Long workflowId) {
        log.info("Loading workflow {}", workflowId);
        WorkflowRequestEntity workflowRequest = workflowRequestRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        return toWorkflowDetailResponse(workflowRequest);
    }

    public List<AuditLogResponse> getAuditLogs(Long workflowId) {
        log.info("Loading audit logs for workflow {}", workflowId);
        if (!workflowRequestRepository.existsById(workflowId)) {
            throw new WorkflowNotFoundException(workflowId);
        }

        return auditLogService.getAuditLogsForWorkflow(workflowId);
    }

    private void saveRequestReceivedAuditLog(WorkflowRequestEntity workflowRequest) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.REQUEST_RECEIVED,
                "Account-opening request was received",
                """
                {
                  "sourceSystem": "DIGITAL_CHANNEL"
                }
                """
        );
    }

    private void saveValidationPassedAuditLog(WorkflowRequestEntity workflowRequest) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.VALIDATION_PASSED,
                "Account-opening request passed validation",
                """
                {
                  "minimumAge": 18
                }
                """
        );
    }

    private void savePayloadMappedAuditLog(
            WorkflowRequestEntity workflowRequest,
            AccountOpeningRequest request,
            CoreBankingPayload coreBankingPayload
    ) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.PAYLOAD_MAPPED,
                "Mapped account-opening request to core banking payload",
                toJson(new PayloadMappedMetadata(
                        request.getAccountType().name(),
                        coreBankingPayload.getProductCode()
                ))
        );
    }

    private void saveValidationFailedAuditLog(WorkflowRequestEntity workflowRequest, String failureReason) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.VALIDATION_FAILED,
                "Account-opening request failed validation",
                toJson(new ValidationFailureMetadata(failureReason))
        );
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

    private void transitionWorkflow(WorkflowRequestEntity workflowRequest, WorkflowStatus nextStatus) {
        WorkflowStatus currentStatus = workflowRequest.getStatus();
        statusTransitionValidator.validateTransition(workflowRequest.getStatus(), nextStatus);
        log.info(
                "Workflow {} with correlationId {} transitioned from {} to {}",
                workflowRequest.getId(),
                workflowRequest.getCorrelationId(),
                currentStatus,
                nextStatus
        );
        workflowRequest.setStatus(nextStatus);
    }

    private WorkflowDetailResponse toWorkflowDetailResponse(WorkflowRequestEntity workflowRequest) {
        return new WorkflowDetailResponse(
                workflowRequest.getId(),
                workflowRequest.getWorkflowType(),
                workflowRequest.getSourceSystem(),
                workflowRequest.getStatus(),
                workflowRequest.getCorrelationId(),
                workflowRequest.getIdempotencyKey(),
                workflowRequest.getOriginalPayload(),
                workflowRequest.getMappedPayload(),
                workflowRequest.getFailureReason(),
                workflowRequest.getRetryCount(),
                workflowRequest.getCreatedAt(),
                workflowRequest.getUpdatedAt()
        );
    }

    private String toJson(AccountOpeningRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize account-opening request", exception);
        }
    }

    private String toJson(CoreBankingPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize core banking payload", exception);
        }
    }

    private String toJson(ValidationFailureMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize validation failure metadata", exception);
        }
    }

    private String toJson(PayloadMappedMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize payload mapped metadata", exception);
        }
    }

    private record ValidationFailureMetadata(String failureReason) {
    }

    private record PayloadMappedMetadata(String sourceAccountType, String targetProductCode) {
    }
}
