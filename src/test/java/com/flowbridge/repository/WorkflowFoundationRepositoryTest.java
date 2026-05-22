package com.flowbridge.repository;

import com.flowbridge.entity.AuditLogEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkflowFoundationRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private WorkflowRequestRepository workflowRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndFindsWorkflowRequestByCorrelationId() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.RECEIVED);
        workflowRequest.setCorrelationId("test-correlation-001");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "C123",
                  "fullName": "Alice Chen"
                }
                """);

        workflowRequestRepository.saveAndFlush(workflowRequest);
        entityManager.clear();

        Optional<WorkflowRequestEntity> savedWorkflow =
                workflowRequestRepository.findByCorrelationId("test-correlation-001");

        assertThat(savedWorkflow).isPresent();
        assertThat(savedWorkflow.get().getWorkflowType()).isEqualTo(WorkflowType.ACCOUNT_OPENING);
        assertThat(savedWorkflow.get().getStatus()).isEqualTo(WorkflowStatus.RECEIVED);
        assertThat(savedWorkflow.get().getRetryCount()).isZero();
        assertThat(savedWorkflow.get().getOriginalPayload()).contains("Alice Chen");
        assertThat(savedWorkflow.get().getCreatedAt()).isNotNull();
    }

    @Test
    void savesAuditLogForWorkflowRequest() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.RECEIVED);
        workflowRequest.setCorrelationId("test-correlation-002");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "C456",
                  "fullName": "Jordan Lee"
                }
                """);
        WorkflowRequestEntity savedWorkflow = workflowRequestRepository.saveAndFlush(workflowRequest);

        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setWorkflowRequest(savedWorkflow);
        auditLog.setCorrelationId(savedWorkflow.getCorrelationId());
        auditLog.setEventType(AuditEventType.REQUEST_RECEIVED);
        auditLog.setMessage("Account-opening request was received");
        auditLog.setMetadata("""
                {
                  "sourceSystem": "DIGITAL_CHANNEL"
                }
                """);

        auditLogRepository.saveAndFlush(auditLog);
        entityManager.clear();

        List<AuditLogEntity> auditLogs =
                auditLogRepository.findByWorkflowRequest_IdOrderByCreatedAtAsc(savedWorkflow.getId());

        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.getFirst().getEventType()).isEqualTo(AuditEventType.REQUEST_RECEIVED);
        assertThat(auditLogs.getFirst().getCorrelationId()).isEqualTo("test-correlation-002");
        assertThat(auditLogs.getFirst().getMetadata()).contains("DIGITAL_CHANNEL");
        assertThat(auditLogs.getFirst().getCreatedAt()).isNotNull();
    }
}
