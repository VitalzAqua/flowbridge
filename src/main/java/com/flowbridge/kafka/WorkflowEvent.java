package com.flowbridge.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public WorkflowEvent(
            @JsonProperty("workflowId") Long workflowId,
            @JsonProperty("workflowType") WorkflowType workflowType,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("timestamp") Instant timestamp
    ) {
        this.workflowId = workflowId;
        this.workflowType = workflowType;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.timestamp = timestamp;
    }
}
