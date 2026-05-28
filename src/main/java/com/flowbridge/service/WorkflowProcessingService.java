package com.flowbridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.dto.CoreBankingPayload;
import com.flowbridge.dto.CoreBankingResponse;
import com.flowbridge.entity.ExternalSystemResponseEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.ExternalSystemStatus;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import com.flowbridge.exception.WorkflowNotFoundException;
import com.flowbridge.kafka.WorkflowEvent;
import com.flowbridge.kafka.WorkflowEventProducer;
import com.flowbridge.repository.ExternalSystemResponseRepository;
import com.flowbridge.repository.WorkflowRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowProcessingService.class);

    private final WorkflowRequestRepository workflowRequestRepository;
    private final ExternalSystemResponseRepository externalSystemResponseRepository;
    private final AuditLogService auditLogService;
    private final MockCoreBankingService mockCoreBankingService;
    private final WorkflowStatusTransitionValidator statusTransitionValidator;
    private final ObjectMapper objectMapper;

    public WorkflowProcessingService(
            WorkflowRequestRepository workflowRequestRepository,
            ExternalSystemResponseRepository externalSystemResponseRepository,
            AuditLogService auditLogService,
            MockCoreBankingService mockCoreBankingService,
            WorkflowStatusTransitionValidator statusTransitionValidator,
            ObjectMapper objectMapper
    ) {
        this.workflowRequestRepository = workflowRequestRepository;
        this.externalSystemResponseRepository = externalSystemResponseRepository;
        this.auditLogService = auditLogService;
        this.mockCoreBankingService = mockCoreBankingService;
        this.statusTransitionValidator = statusTransitionValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processWorkflowEvent(WorkflowEvent event) {
        if (!isAccountOpeningMappedEvent(event)) {
            log.warn(
                    "Ignoring unsupported workflow event {} for workflow {} with correlationId {}",
                    event.getEventType(),
                    event.getWorkflowId(),
                    event.getCorrelationId()
            );
            return;
        }

        WorkflowRequestEntity workflowRequest = workflowRequestRepository.findByIdForUpdate(event.getWorkflowId())
                .orElseThrow(() -> new WorkflowNotFoundException(event.getWorkflowId()));

        if (!workflowRequest.getIdempotencyKey().equals(event.getIdempotencyKey())) {
            log.info(
                    "Skipping event {} for workflow {} because event idempotencyKey {} does not match workflow idempotencyKey {}",
                    event.getEventType(),
                    workflowRequest.getId(),
                    event.getIdempotencyKey(),
                    workflowRequest.getIdempotencyKey()
            );
            saveDuplicateEventSkippedAuditLog(
                    workflowRequest,
                    event,
                    "Event idempotency key does not match workflow idempotency key"
            );
            return;
        }

        if (!isProcessableStatus(workflowRequest.getStatus())) {
            log.info(
                    "Skipping duplicate or stale event {} for workflow {} with correlationId {} in status {}",
                    event.getEventType(),
                    workflowRequest.getId(),
                    workflowRequest.getCorrelationId(),
                    workflowRequest.getStatus()
            );
            saveDuplicateEventSkippedAuditLog(
                    workflowRequest,
                    event,
                    "Workflow status " + workflowRequest.getStatus() + " is not processable"
            );
            return;
        }

        transitionWorkflow(workflowRequest, WorkflowStatus.PROCESSING);
        WorkflowRequestEntity processingWorkflow = workflowRequestRepository.save(workflowRequest);
        saveProcessingStartedAuditLog(processingWorkflow);

        try {
            CoreBankingPayload mappedPayload = readMappedPayload(processingWorkflow);
            saveExternalSystemCallStartedAuditLog(processingWorkflow);

            CoreBankingResponse coreBankingResponse = mockCoreBankingService.openAccount(mappedPayload);
            saveExternalSystemResponse(processingWorkflow, coreBankingResponse);

            if (coreBankingResponse.isSuccessful()) {
                completeWorkflow(processingWorkflow, coreBankingResponse);
                return;
            }

            failWorkflow(processingWorkflow, coreBankingResponse);
        } catch (RuntimeException exception) {
            log.error(
                    "Workflow {} with correlationId {} failed during async processing",
                    processingWorkflow.getId(),
                    processingWorkflow.getCorrelationId(),
                    exception
            );
            failWorkflowAfterUnexpectedException(processingWorkflow, exception);
        }
    }

    private boolean isAccountOpeningMappedEvent(WorkflowEvent event) {
        return event.getWorkflowType() == WorkflowType.ACCOUNT_OPENING
                && WorkflowEventProducer.ACCOUNT_OPENING_MAPPED_EVENT.equals(event.getEventType());
    }

    private CoreBankingPayload readMappedPayload(WorkflowRequestEntity workflowRequest) {
        try {
            return objectMapper.readValue(workflowRequest.getMappedPayload(), CoreBankingPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to deserialize mapped payload for workflow " + workflowRequest.getId(),
                    exception
            );
        }
    }

    private boolean isProcessableStatus(WorkflowStatus status) {
        return status == WorkflowStatus.MAPPED || status == WorkflowStatus.RETRYING;
    }

    private void completeWorkflow(WorkflowRequestEntity workflowRequest, CoreBankingResponse coreBankingResponse) {
        saveExternalSystemCallSucceededAuditLog(workflowRequest, coreBankingResponse);
        transitionWorkflow(workflowRequest, WorkflowStatus.COMPLETED);
        WorkflowRequestEntity completedWorkflow = workflowRequestRepository.save(workflowRequest);
        saveWorkflowCompletedAuditLog(completedWorkflow, coreBankingResponse);
    }

    private void failWorkflow(WorkflowRequestEntity workflowRequest, CoreBankingResponse coreBankingResponse) {
        saveExternalSystemCallFailedAuditLog(workflowRequest, coreBankingResponse);
        workflowRequest.setFailureReason(coreBankingResponse.getMessage());
        transitionWorkflow(workflowRequest, WorkflowStatus.FAILED);
        WorkflowRequestEntity failedWorkflow = workflowRequestRepository.save(workflowRequest);
        saveWorkflowFailedAuditLog(failedWorkflow, coreBankingResponse);
    }

    private void failWorkflowAfterUnexpectedException(
            WorkflowRequestEntity workflowRequest,
            RuntimeException exception
    ) {
        String failureReason = "Unexpected workflow processing failure: " + exception.getMessage();
        workflowRequest.setFailureReason(failureReason);
        transitionWorkflow(workflowRequest, WorkflowStatus.FAILED);
        WorkflowRequestEntity failedWorkflow = workflowRequestRepository.save(workflowRequest);
        saveWorkflowFailedAuditLog(failedWorkflow, failureReason);
    }

    private void saveExternalSystemResponse(
            WorkflowRequestEntity workflowRequest,
            CoreBankingResponse coreBankingResponse
    ) {
        ExternalSystemResponseEntity externalSystemResponse = new ExternalSystemResponseEntity();
        externalSystemResponse.setWorkflowRequest(workflowRequest);
        externalSystemResponse.setExternalReferenceId(coreBankingResponse.getExternalReferenceId());
        externalSystemResponse.setStatus(coreBankingResponse.getStatus());
        externalSystemResponse.setErrorCode(coreBankingResponse.getErrorCode());
        externalSystemResponse.setMessage(coreBankingResponse.getMessage());

        externalSystemResponseRepository.save(externalSystemResponse);
    }

    private void transitionWorkflow(WorkflowRequestEntity workflowRequest, WorkflowStatus nextStatus) {
        WorkflowStatus currentStatus = workflowRequest.getStatus();
        statusTransitionValidator.validateTransition(currentStatus, nextStatus);
        workflowRequest.setStatus(nextStatus);
        log.info(
                "Workflow {} with correlationId {} transitioned from {} to {} during async processing",
                workflowRequest.getId(),
                workflowRequest.getCorrelationId(),
                currentStatus,
                nextStatus
        );
    }

    private void saveProcessingStartedAuditLog(WorkflowRequestEntity workflowRequest) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.PROCESSING_STARTED,
                "Kafka consumer started processing workflow",
                "{}"
        );
    }

    private void saveExternalSystemCallStartedAuditLog(WorkflowRequestEntity workflowRequest) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.EXTERNAL_SYSTEM_CALL_STARTED,
                "Started mock core banking account-opening call",
                "{}"
        );
    }

    private void saveExternalSystemCallSucceededAuditLog(
            WorkflowRequestEntity workflowRequest,
            CoreBankingResponse coreBankingResponse
    ) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.EXTERNAL_SYSTEM_CALL_SUCCEEDED,
                "Mock core banking account-opening call succeeded",
                toJson(new ExternalCallMetadata(
                        coreBankingResponse.getStatus(),
                        coreBankingResponse.getExternalReferenceId(),
                        coreBankingResponse.getErrorCode(),
                        coreBankingResponse.getMessage()
                ))
        );
    }

    private void saveExternalSystemCallFailedAuditLog(
            WorkflowRequestEntity workflowRequest,
            CoreBankingResponse coreBankingResponse
    ) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.EXTERNAL_SYSTEM_CALL_FAILED,
                "Mock core banking account-opening call failed",
                toJson(new ExternalCallMetadata(
                        coreBankingResponse.getStatus(),
                        coreBankingResponse.getExternalReferenceId(),
                        coreBankingResponse.getErrorCode(),
                        coreBankingResponse.getMessage()
                ))
        );
    }

    private void saveWorkflowCompletedAuditLog(
            WorkflowRequestEntity workflowRequest,
            CoreBankingResponse coreBankingResponse
    ) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.WORKFLOW_COMPLETED,
                "Workflow completed after successful core banking processing",
                toJson(new WorkflowFinishedMetadata(coreBankingResponse.getExternalReferenceId(), null))
        );
    }

    private void saveWorkflowFailedAuditLog(
            WorkflowRequestEntity workflowRequest,
            CoreBankingResponse coreBankingResponse
    ) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.WORKFLOW_FAILED,
                "Workflow failed after core banking processing",
                toJson(new WorkflowFinishedMetadata(null, coreBankingResponse.getMessage()))
        );
    }

    private void saveWorkflowFailedAuditLog(
            WorkflowRequestEntity workflowRequest,
            String failureReason
    ) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.WORKFLOW_FAILED,
                "Workflow failed during async processing",
                toJson(new WorkflowFinishedMetadata(null, failureReason))
        );
    }

    private void saveDuplicateEventSkippedAuditLog(
            WorkflowRequestEntity workflowRequest,
            WorkflowEvent event,
            String reason
    ) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.DUPLICATE_EVENT_SKIPPED,
                "Skipped duplicate or stale Kafka event",
                toJson(new DuplicateEventMetadata(
                        event.getEventType(),
                        event.getIdempotencyKey(),
                        event.getTimestamp().toString(),
                        reason
                ))
        );
    }

    private String toJson(Object metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize workflow processing audit metadata", exception);
        }
    }

    private record ExternalCallMetadata(
            ExternalSystemStatus status,
            String externalReferenceId,
            String errorCode,
            String message
    ) {
    }

    private record WorkflowFinishedMetadata(String externalReferenceId, String failureReason) {
    }

    private record DuplicateEventMetadata(
            String eventType,
            String idempotencyKey,
            String eventTimestamp,
            String reason
    ) {
    }
}
