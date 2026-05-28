package com.flowbridge.exception;

import com.flowbridge.enums.WorkflowStatus;

public class NonRetryableWorkflowException extends RuntimeException {

    public NonRetryableWorkflowException(Long workflowId, WorkflowStatus status) {
        super("Workflow " + workflowId + " cannot be retried from status " + status);
    }

    public NonRetryableWorkflowException(Long workflowId, int retryCount, int maxRetryCount) {
        super("Workflow " + workflowId + " has reached max retry count "
                + maxRetryCount + " with current retry count " + retryCount);
    }

    public NonRetryableWorkflowException(Long workflowId, String reason) {
        super("Workflow " + workflowId + " cannot be retried: " + reason);
    }
}
