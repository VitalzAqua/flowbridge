package com.flowbridge.repository;

import com.flowbridge.TestcontainersConfiguration;
import com.flowbridge.entity.AuditLogEntity;
import com.flowbridge.entity.ExternalSystemResponseEntity;
import com.flowbridge.entity.OutboxEventEntity;
import com.flowbridge.entity.RetryAttemptEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.ExternalSystemStatus;
import com.flowbridge.enums.OutboxEventStatus;
import com.flowbridge.enums.RetryAttemptStatus;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkflowFoundationRepositoryTest {

    @Autowired
    private WorkflowRequestRepository workflowRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ExternalSystemResponseRepository externalSystemResponseRepository;

    @Autowired
    private RetryAttemptRepository retryAttemptRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndFindsWorkflowRequestByCorrelationId() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.RECEIVED);
        workflowRequest.setCorrelationId("test-correlation-001");
        workflowRequest.setIdempotencyKey("ACCOUNT_OPENING:test-correlation-001");
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
        assertThat(savedWorkflow.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void updatesWorkflowUpdatedAtWhenWorkflowChanges() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.RECEIVED);
        workflowRequest.setCorrelationId("test-correlation-003");
        workflowRequest.setIdempotencyKey("ACCOUNT_OPENING:test-correlation-003");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "C789",
                  "fullName": "Mina Patel"
                }
                """);

        WorkflowRequestEntity savedWorkflow = workflowRequestRepository.saveAndFlush(workflowRequest);
        entityManager.clear();

        WorkflowRequestEntity workflowToUpdate = workflowRequestRepository.findById(savedWorkflow.getId()).orElseThrow();
        assertThat(workflowToUpdate.getCreatedAt()).isEqualTo(workflowToUpdate.getUpdatedAt());

        workflowToUpdate.setStatus(WorkflowStatus.VALIDATED);
        workflowRequestRepository.saveAndFlush(workflowToUpdate);
        entityManager.clear();

        WorkflowRequestEntity updatedWorkflow = workflowRequestRepository.findById(savedWorkflow.getId()).orElseThrow();

        assertThat(updatedWorkflow.getUpdatedAt()).isAfter(updatedWorkflow.getCreatedAt());
    }

    @Test
    void savesAuditLogForWorkflowRequest() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.RECEIVED);
        workflowRequest.setCorrelationId("test-correlation-002");
        workflowRequest.setIdempotencyKey("ACCOUNT_OPENING:test-correlation-002");
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

    @Test
    void savesRetryAttemptForFailedWorkflow() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.FAILED);
        workflowRequest.setCorrelationId("test-correlation-004");
        workflowRequest.setIdempotencyKey("ACCOUNT_OPENING:test-correlation-004");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "FAIL-123"
                }
                """);
        WorkflowRequestEntity savedWorkflow = workflowRequestRepository.saveAndFlush(workflowRequest);

        RetryAttemptEntity retryAttempt = new RetryAttemptEntity();
        retryAttempt.setWorkflowRequest(savedWorkflow);
        retryAttempt.setAttemptNumber(1);
        retryAttempt.setStatus(RetryAttemptStatus.REQUESTED);
        retryAttempt.setFailureReason("Core banking rejected the request");

        retryAttemptRepository.saveAndFlush(retryAttempt);
        entityManager.clear();

        List<RetryAttemptEntity> retryAttempts =
                retryAttemptRepository.findByWorkflowRequest_IdOrderByAttemptNumberAsc(savedWorkflow.getId());

        assertThat(retryAttempts).hasSize(1);
        assertThat(retryAttempts.getFirst().getAttemptNumber()).isEqualTo(1);
        assertThat(retryAttempts.getFirst().getStatus()).isEqualTo(RetryAttemptStatus.REQUESTED);
        assertThat(retryAttempts.getFirst().getFailureReason()).contains("Core banking rejected");
        assertThat(retryAttempts.getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    void findsExistingSuccessfulExternalSystemResponseForWorkflow() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.COMPLETED);
        workflowRequest.setCorrelationId("test-correlation-005");
        workflowRequest.setIdempotencyKey("ACCOUNT_OPENING:test-correlation-005");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "C123"
                }
                """);
        WorkflowRequestEntity savedWorkflow = workflowRequestRepository.saveAndFlush(workflowRequest);

        ExternalSystemResponseEntity externalSystemResponse = new ExternalSystemResponseEntity();
        externalSystemResponse.setWorkflowRequest(savedWorkflow);
        externalSystemResponse.setExternalReferenceId("CORE-123");
        externalSystemResponse.setStatus(ExternalSystemStatus.SUCCESS);
        externalSystemResponse.setMessage("Account created successfully");

        externalSystemResponseRepository.saveAndFlush(externalSystemResponse);
        entityManager.clear();

        boolean successResponseExists = externalSystemResponseRepository.existsByWorkflowRequest_IdAndStatus(
                savedWorkflow.getId(),
                ExternalSystemStatus.SUCCESS
        );
        boolean failedResponseExists = externalSystemResponseRepository.existsByWorkflowRequest_IdAndStatus(
                savedWorkflow.getId(),
                ExternalSystemStatus.FAILED
        );

        assertThat(successResponseExists).isTrue();
        assertThat(failedResponseExists).isFalse();
    }

    @Test
    void savesPendingOutboxEventForWorkflow() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.MAPPED);
        workflowRequest.setCorrelationId("test-correlation-006");
        workflowRequest.setIdempotencyKey("ACCOUNT_OPENING:test-correlation-006");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "C123"
                }
                """);
        WorkflowRequestEntity savedWorkflow = workflowRequestRepository.saveAndFlush(workflowRequest);

        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setWorkflowRequest(savedWorkflow);
        outboxEvent.setEventType("ACCOUNT_OPENING_MAPPED");
        outboxEvent.setIdempotencyKey(savedWorkflow.getIdempotencyKey());
        outboxEvent.setPayload("""
                {
                  "workflowId": %d,
                  "eventType": "ACCOUNT_OPENING_MAPPED",
                  "idempotencyKey": "%s"
                }
                """.formatted(savedWorkflow.getId(), savedWorkflow.getIdempotencyKey()));
        outboxEvent.setStatus(OutboxEventStatus.PENDING);

        outboxEventRepository.saveAndFlush(outboxEvent);
        entityManager.clear();

        List<OutboxEventEntity> outboxEvents =
                outboxEventRepository.findByWorkflowRequest_IdOrderByCreatedAtAsc(savedWorkflow.getId());

        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.getFirst().getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvents.getFirst().getIdempotencyKey())
                .isEqualTo("ACCOUNT_OPENING:test-correlation-006");
    }
}
