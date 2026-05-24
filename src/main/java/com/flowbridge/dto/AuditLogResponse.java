package com.flowbridge.dto;

import com.flowbridge.enums.AuditEventType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AuditLogResponse {

    private final Long auditLogId;
    private final Long workflowId;
    private final String correlationId;
    private final AuditEventType eventType;
    private final String message;
    private final String metadata;
    private final LocalDateTime createdAt;

    public AuditLogResponse(
            Long auditLogId,
            Long workflowId,
            String correlationId,
            AuditEventType eventType,
            String message,
            String metadata,
            LocalDateTime createdAt
    ) {
        this.auditLogId = auditLogId;
        this.workflowId = workflowId;
        this.correlationId = correlationId;
        this.eventType = eventType;
        this.message = message;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }
}
