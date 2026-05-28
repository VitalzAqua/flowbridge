package com.flowbridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.dto.WorkflowResponse;
import com.flowbridge.entity.RetryAttemptEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.RetryAttemptStatus;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.exception.NonRetryableWorkflowException;
import com.flowbridge.exception.WorkflowNotFoundException;
import com.flowbridge.kafka.WorkflowEvent;
import com.flowbridge.kafka.WorkflowEventProducer;
import com.flowbridge.repository.RetryAttemptRepository;
import com.flowbridge.repository.WorkflowRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetryService {

    private static final Logger log = LoggerFactory.getLogger(RetryService.class);
    private static final int MAX_RETRY_COUNT = 3;

    private final WorkflowRequestRepository workflowRequestRepository;
    private final RetryAttemptRepository retryAttemptRepository;
    private final AuditLogService auditLogService;
    private final WorkflowStatusTransitionValidator statusTransitionValidator;
    private final WorkflowEventProducer workflowEventProducer;
    private final ObjectMapper objectMapper;

    public RetryService(
            WorkflowRequestRepository workflowRequestRepository,
            RetryAttemptRepository retryAttemptRepository,
            AuditLogService auditLogService,
            WorkflowStatusTransitionValidator statusTransitionValidator,
            WorkflowEventProducer workflowEventProducer,
            ObjectMapper objectMapper
    ) {
        this.workflowRequestRepository = workflowRequestRepository;
        this.retryAttemptRepository = retryAttemptRepository;
        this.auditLogService = auditLogService;
        this.statusTransitionValidator = statusTransitionValidator;
        this.workflowEventProducer = workflowEventProducer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowResponse retryWorkflow(Long workflowId) {
        WorkflowRequestEntity workflowRequest = workflowRequestRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));

        if (workflowRequest.getStatus() != WorkflowStatus.FAILED) {
            throw new NonRetryableWorkflowException(workflowId, workflowRequest.getStatus());
        }

        int currentRetryCount = workflowRequest.getRetryCount();
        if (currentRetryCount >= MAX_RETRY_COUNT) {
            throw new NonRetryableWorkflowException(workflowId, currentRetryCount, MAX_RETRY_COUNT);
        }

        if (workflowRequest.getMappedPayload() == null || workflowRequest.getMappedPayload().isBlank()) {
            throw new NonRetryableWorkflowException(workflowId, "no mapped payload exists to reprocess");
        }

        int nextAttemptNumber = currentRetryCount + 1;
        String previousFailureReason = workflowRequest.getFailureReason();

        RetryAttemptEntity retryAttempt = saveRetryAttempt(
                workflowRequest,
                nextAttemptNumber,
                previousFailureReason,
                RetryAttemptStatus.REQUESTED
        );

        workflowRequest.setRetryCount(nextAttemptNumber);
        workflowRequest.setFailureReason(null);
        transitionWorkflow(workflowRequest, WorkflowStatus.RETRYING);
        WorkflowRequestEntity retryingWorkflow = workflowRequestRepository.save(workflowRequest);

        saveRetryRequestedAuditLog(retryingWorkflow, nextAttemptNumber, previousFailureReason);
        WorkflowEvent workflowEvent = workflowEventProducer.publishAccountOpeningMappedEvent(retryingWorkflow);
        retryAttempt.setStatus(RetryAttemptStatus.EVENT_PUBLISHED);
        retryAttemptRepository.save(retryAttempt);
        saveKafkaEventPublishedAuditLog(retryingWorkflow, workflowEvent, nextAttemptNumber);

        return new WorkflowResponse(
                retryingWorkflow.getId(),
                retryingWorkflow.getWorkflowType(),
                retryingWorkflow.getStatus(),
                retryingWorkflow.getCorrelationId(),
                "Retry requested for failed workflow"
        );
    }

    private RetryAttemptEntity saveRetryAttempt(
            WorkflowRequestEntity workflowRequest,
            int attemptNumber,
            String failureReason,
            RetryAttemptStatus status
    ) {
        RetryAttemptEntity retryAttempt = new RetryAttemptEntity();
        retryAttempt.setWorkflowRequest(workflowRequest);
        retryAttempt.setAttemptNumber(attemptNumber);
        retryAttempt.setStatus(status);
        retryAttempt.setFailureReason(failureReason);

        return retryAttemptRepository.save(retryAttempt);
    }

    private void transitionWorkflow(WorkflowRequestEntity workflowRequest, WorkflowStatus nextStatus) {
        WorkflowStatus currentStatus = workflowRequest.getStatus();
        statusTransitionValidator.validateTransition(currentStatus, nextStatus);
        workflowRequest.setStatus(nextStatus);
        log.info(
                "Workflow {} with correlationId {} transitioned from {} to {} for retry",
                workflowRequest.getId(),
                workflowRequest.getCorrelationId(),
                currentStatus,
                nextStatus
        );
    }

    private void saveRetryRequestedAuditLog(
            WorkflowRequestEntity workflowRequest,
            int attemptNumber,
            String previousFailureReason
    ) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.RETRY_REQUESTED,
                "Retry requested for failed workflow",
                toJson(new RetryRequestedMetadata(attemptNumber, previousFailureReason))
        );
    }

    private void saveKafkaEventPublishedAuditLog(
            WorkflowRequestEntity workflowRequest,
            WorkflowEvent workflowEvent,
            int attemptNumber
    ) {
        auditLogService.writeAuditLog(
                workflowRequest,
                AuditEventType.KAFKA_EVENT_PUBLISHED,
                "Published retry workflow event to Kafka",
                toJson(new RetryKafkaEventPublishedMetadata(
                        attemptNumber,
                        workflowEvent.getEventType(),
                        workflowEvent.getTimestamp().toString()
                ))
        );
    }

    private String toJson(Object metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize retry audit metadata", exception);
        }
    }

    private record RetryRequestedMetadata(int attemptNumber, String previousFailureReason) {
    }

    private record RetryKafkaEventPublishedMetadata(int attemptNumber, String eventType, String timestamp) {
    }
}
