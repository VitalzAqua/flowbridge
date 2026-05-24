package com.flowbridge.exception;

public class WorkflowNotFoundException extends RuntimeException {

    public WorkflowNotFoundException(Long workflowId) {
        super("Workflow " + workflowId + " was not found");
    }
}
