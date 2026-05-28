package com.flowbridge.kafka;

import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.exception.KafkaEventPublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class WorkflowEventProducer {

    public static final String ACCOUNT_OPENING_MAPPED_EVENT = "ACCOUNT_OPENING_MAPPED";

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventProducer.class);

    private final KafkaTemplate<String, WorkflowEvent> kafkaTemplate;
    private final String workflowEventsTopic;

    public WorkflowEventProducer(
            KafkaTemplate<String, WorkflowEvent> kafkaTemplate,
            @Value("${flowbridge.kafka.workflow-events-topic}") String workflowEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.workflowEventsTopic = workflowEventsTopic;
    }

    public WorkflowEvent publishAccountOpeningMappedEvent(WorkflowRequestEntity workflowRequest) {
        WorkflowEvent event = new WorkflowEvent(
                workflowRequest.getId(),
                workflowRequest.getWorkflowType(),
                ACCOUNT_OPENING_MAPPED_EVENT,
                workflowRequest.getCorrelationId(),
                workflowRequest.getIdempotencyKey(),
                Instant.now()
        );

        publishEvent(event);
        return event;
    }

    public void publishEvent(WorkflowEvent event) {
        try {
            kafkaTemplate.send(workflowEventsTopic, event.getIdempotencyKey(), event).join();
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to publish Kafka event {} for workflow {} with correlationId {} to topic {}",
                    event.getEventType(),
                    event.getWorkflowId(),
                    event.getCorrelationId(),
                    workflowEventsTopic,
                    exception
            );
            throw new KafkaEventPublishException(event.getEventType(), event.getWorkflowId(), exception);
        }

        log.info(
                "Published Kafka event {} for workflow {} with correlationId {} to topic {}",
                event.getEventType(),
                event.getWorkflowId(),
                event.getCorrelationId(),
                workflowEventsTopic
        );
    }
}
