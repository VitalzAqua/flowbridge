package com.flowbridge.dto;

import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class WorkflowDetailResponse {

    private final Long workflowId;
    private final WorkflowType workflowType;
    private final String sourceSystem;
    private final WorkflowStatus status;
    private final String correlationId;
    private final String idempotencyKey;
    private final String originalPayload;
    private final String mappedPayload;
    private final String failureReason;
    private final Integer retryCount;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public WorkflowDetailResponse(
            Long workflowId,
            WorkflowType workflowType,
            String sourceSystem,
            WorkflowStatus status,
            String correlationId,
            String idempotencyKey,
            String originalPayload,
            String mappedPayload,
            String failureReason,
            Integer retryCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.workflowId = workflowId;
        this.workflowType = workflowType;
        this.sourceSystem = sourceSystem;
        this.status = status;
        this.correlationId = correlationId;
        this.idempotencyKey = idempotencyKey;
        this.originalPayload = originalPayload;
        this.mappedPayload = mappedPayload;
        this.failureReason = failureReason;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
