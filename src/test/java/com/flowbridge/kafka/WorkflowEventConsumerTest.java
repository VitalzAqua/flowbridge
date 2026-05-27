package com.flowbridge.kafka;

import com.flowbridge.enums.WorkflowType;
import com.flowbridge.service.WorkflowProcessingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkflowEventConsumerTest {

    private final WorkflowProcessingService workflowProcessingService = mock(WorkflowProcessingService.class);
    private final WorkflowEventConsumer workflowEventConsumer = new WorkflowEventConsumer(workflowProcessingService);

    @Test
    void delegatesKafkaEventToWorkflowProcessingService() {
        WorkflowEvent event = new WorkflowEvent(
                10L,
                WorkflowType.ACCOUNT_OPENING,
                WorkflowEventProducer.ACCOUNT_OPENING_MAPPED_EVENT,
                "corr-123",
                Instant.parse("2026-05-27T10:00:00Z")
        );

        workflowEventConsumer.consume(event);

        verify(workflowProcessingService).processWorkflowEvent(event);
    }
}
