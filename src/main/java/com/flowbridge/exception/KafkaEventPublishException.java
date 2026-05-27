package com.flowbridge.exception;

public class KafkaEventPublishException extends RuntimeException {

    public KafkaEventPublishException(String eventType, Long workflowId, Throwable cause) {
        super("Failed to publish Kafka event " + eventType + " for workflow " + workflowId, cause);
    }
}
