package com.flowbridge.kafka;

import com.flowbridge.service.WorkflowProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventConsumer.class);

    private final WorkflowProcessingService workflowProcessingService;

    public WorkflowEventConsumer(WorkflowProcessingService workflowProcessingService) {
        this.workflowProcessingService = workflowProcessingService;
    }

    @KafkaListener(
            topics = "${flowbridge.kafka.workflow-events-topic}",
            groupId = "${flowbridge.kafka.consumer-group-id}"
    )
    public void consume(WorkflowEvent event) {
        log.info(
                "Received Kafka event {} for workflow {} with correlationId {}",
                event.getEventType(),
                event.getWorkflowId(),
                event.getCorrelationId()
        );

        workflowProcessingService.processWorkflowEvent(event);
    }
}
