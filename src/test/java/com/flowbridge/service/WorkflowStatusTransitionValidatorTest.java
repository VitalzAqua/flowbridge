package com.flowbridge.service;

import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.exception.InvalidWorkflowStateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowStatusTransitionValidatorTest {

    private final WorkflowStatusTransitionValidator validator = new WorkflowStatusTransitionValidator();

    @Test
    void allowsSynchronousMvpTransitions() {
        validator.validateTransition(WorkflowStatus.RECEIVED, WorkflowStatus.VALIDATED);
        validator.validateTransition(WorkflowStatus.RECEIVED, WorkflowStatus.FAILED);
        validator.validateTransition(WorkflowStatus.VALIDATED, WorkflowStatus.MAPPED);
        validator.validateTransition(WorkflowStatus.VALIDATED, WorkflowStatus.FAILED);
    }

    @Test
    void allowsFutureProcessingAndRetryTransitions() {
        validator.validateTransition(WorkflowStatus.MAPPED, WorkflowStatus.PROCESSING);
        validator.validateTransition(WorkflowStatus.MAPPED, WorkflowStatus.FAILED);
        validator.validateTransition(WorkflowStatus.PROCESSING, WorkflowStatus.COMPLETED);
        validator.validateTransition(WorkflowStatus.PROCESSING, WorkflowStatus.FAILED);
        validator.validateTransition(WorkflowStatus.FAILED, WorkflowStatus.RETRYING);
        validator.validateTransition(WorkflowStatus.RETRYING, WorkflowStatus.PROCESSING);
    }

    @Test
    void rejectsInvalidWorkflowStatusTransition() {
        assertThatThrownBy(() -> validator.validateTransition(WorkflowStatus.FAILED, WorkflowStatus.VALIDATED))
                .isInstanceOf(InvalidWorkflowStateException.class)
                .hasMessage("Workflow cannot transition from FAILED to VALIDATED");
    }

    @Test
    void rejectsTransitionFromTerminalCompletedStatus() {
        assertThatThrownBy(() -> validator.validateTransition(WorkflowStatus.COMPLETED, WorkflowStatus.RETRYING))
                .isInstanceOf(InvalidWorkflowStateException.class)
                .hasMessage("Workflow cannot transition from COMPLETED to RETRYING");
    }
}
