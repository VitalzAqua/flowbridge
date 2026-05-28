package com.flowbridge.kafka;

import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import com.flowbridge.exception.KafkaEventPublishException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowEventProducerTest {

    private final KafkaTemplate<String, WorkflowEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private final WorkflowEventProducer workflowEventProducer =
            new WorkflowEventProducer(kafkaTemplate, "flowbridge.workflow.events");

    @Test
    void publishesAccountOpeningMappedEvent() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setStatus(WorkflowStatus.MAPPED);
        workflowRequest.setCorrelationId("corr-123");
        workflowRequest.setIdempotencyKey("ACCOUNT_OPENING:corr-123");

        when(kafkaTemplate.send(
                eq("flowbridge.workflow.events"),
                eq("ACCOUNT_OPENING:corr-123"),
                org.mockito.ArgumentMatchers.any(WorkflowEvent.class)
        )).thenReturn(CompletableFuture.completedFuture(null));

        WorkflowEvent event = workflowEventProducer.publishAccountOpeningMappedEvent(workflowRequest);

        assertThat(event.getWorkflowType()).isEqualTo(WorkflowType.ACCOUNT_OPENING);
        assertThat(event.getEventType()).isEqualTo(WorkflowEventProducer.ACCOUNT_OPENING_MAPPED_EVENT);
        assertThat(event.getCorrelationId()).isEqualTo("corr-123");
        assertThat(event.getIdempotencyKey()).isEqualTo("ACCOUNT_OPENING:corr-123");
        assertThat(event.getTimestamp()).isNotNull();

        verify(kafkaTemplate).send(
                eq("flowbridge.workflow.events"),
                eq("ACCOUNT_OPENING:corr-123"),
                eq(event)
        );
    }

    @Test
    void throwsClearExceptionWhenKafkaPublishFails() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setStatus(WorkflowStatus.MAPPED);
        workflowRequest.setCorrelationId("corr-123");
        workflowRequest.setIdempotencyKey("ACCOUNT_OPENING:corr-123");

        when(kafkaTemplate.send(
                eq("flowbridge.workflow.events"),
                eq("ACCOUNT_OPENING:corr-123"),
                org.mockito.ArgumentMatchers.any(WorkflowEvent.class)
        )).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable")));

        assertThatThrownBy(() -> workflowEventProducer.publishAccountOpeningMappedEvent(workflowRequest))
                .isInstanceOf(KafkaEventPublishException.class)
                .hasMessageContaining("Failed to publish Kafka event ACCOUNT_OPENING_MAPPED");
    }
}
