package com.flowbridge.service;

import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.exception.InvalidWorkflowStateException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class WorkflowStatusTransitionValidator {

    private static final Map<WorkflowStatus, Set<WorkflowStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(WorkflowStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(WorkflowStatus.RECEIVED, EnumSet.of(
                WorkflowStatus.VALIDATED,
                WorkflowStatus.FAILED
        ));
        ALLOWED_TRANSITIONS.put(WorkflowStatus.VALIDATED, EnumSet.of(
                WorkflowStatus.MAPPED,
                WorkflowStatus.FAILED
        ));
        ALLOWED_TRANSITIONS.put(WorkflowStatus.MAPPED, EnumSet.of(
                WorkflowStatus.PROCESSING,
                WorkflowStatus.FAILED
        ));
        ALLOWED_TRANSITIONS.put(WorkflowStatus.PROCESSING, EnumSet.of(
                WorkflowStatus.COMPLETED,
                WorkflowStatus.FAILED
        ));
        ALLOWED_TRANSITIONS.put(WorkflowStatus.FAILED, EnumSet.of(
                WorkflowStatus.RETRYING,
                WorkflowStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(WorkflowStatus.RETRYING, EnumSet.of(
                WorkflowStatus.PROCESSING
        ));
    }

    public void validateTransition(WorkflowStatus currentStatus, WorkflowStatus nextStatus) {
        Set<WorkflowStatus> allowedNextStatuses = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());

        if (!allowedNextStatuses.contains(nextStatus)) {
            throw new InvalidWorkflowStateException(currentStatus, nextStatus);
        }
    }
}
