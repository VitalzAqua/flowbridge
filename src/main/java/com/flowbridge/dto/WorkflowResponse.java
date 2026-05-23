package com.flowbridge.dto;

import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import lombok.Getter;

@Getter
public class WorkflowResponse {

    private Long workflowId;
    private WorkflowType workflowType;
    private WorkflowStatus status;
    private String correlationId;
    private String message;

    public WorkflowResponse(
            Long workflowId,
            WorkflowType workflowType,
            WorkflowStatus status,
            String correlationId,
            String message
    ) {
        this.workflowId = workflowId;
        this.workflowType = workflowType;
        this.status = status;
        this.correlationId = correlationId;
        this.message = message;
    }

}
