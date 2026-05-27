package com.flowbridge.kafka;

import com.flowbridge.enums.WorkflowType;
import lombok.Getter;

import java.time.Instant;

@Getter
public class WorkflowEvent {

    private final Long workflowId;
    private final WorkflowType workflowType;
    private final String eventType;
    private final String correlationId;
    private final Instant timestamp;

    public WorkflowEvent(
            Long workflowId,
            WorkflowType workflowType,
            String eventType,
            String correlationId,
            Instant timestamp
    ) {
        this.workflowId = workflowId;
        this.workflowType = workflowType;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.timestamp = timestamp;
    }
}
