package com.flowbridge.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic workflowEventsTopic(
            @Value("${flowbridge.kafka.workflow-events-topic}") String workflowEventsTopic
    ) {
        return TopicBuilder.name(workflowEventsTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
