package com.flowbridge.exception;

import com.flowbridge.enums.WorkflowStatus;

public class InvalidWorkflowStateException extends RuntimeException {

    public InvalidWorkflowStateException(WorkflowStatus currentStatus, WorkflowStatus nextStatus) {
        super("Workflow cannot transition from " + currentStatus + " to " + nextStatus);
    }
}
