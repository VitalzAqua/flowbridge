package com.flowbridge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowbridge.TestcontainersConfiguration;
import com.flowbridge.entity.AuditLogEntity;
import com.flowbridge.entity.RetryAttemptEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.enums.RetryAttemptStatus;
import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import com.flowbridge.kafka.WorkflowEvent;
import com.flowbridge.kafka.WorkflowEventProducer;
import com.flowbridge.repository.AuditLogRepository;
import com.flowbridge.repository.ExternalSystemResponseRepository;
import com.flowbridge.repository.RetryAttemptRepository;
import com.flowbridge.repository.WorkflowRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
class AccountOpeningWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowRequestRepository workflowRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ExternalSystemResponseRepository externalSystemResponseRepository;

    @Autowired
    private RetryAttemptRepository retryAttemptRepository;

    @MockitoBean
    private WorkflowEventProducer workflowEventProducer;

    @BeforeEach
    void setUp() {
        retryAttemptRepository.deleteAll();
        auditLogRepository.deleteAll();
        externalSystemResponseRepository.deleteAll();
        workflowRequestRepository.deleteAll();

        when(workflowEventProducer.publishAccountOpeningMappedEvent(any(WorkflowRequestEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowRequestEntity workflowRequest = invocation.getArgument(0);
                    return new WorkflowEvent(
                            workflowRequest.getId(),
                            workflowRequest.getWorkflowType(),
                            WorkflowEventProducer.ACCOUNT_OPENING_MAPPED_EVENT,
                            workflowRequest.getCorrelationId(),
                            Instant.parse("2026-05-28T10:00:00Z")
                    );
                });
    }

    @Test
    void accountOpeningRequestCreatesMappedWorkflowAndAuditLogs() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/workflows/account-opening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "C123",
                                  "fullName": "Alice Chen",
                                  "dateOfBirth": "2001-05-01",
                                  "accountType": "SAVINGS",
                                  "advisorCode": "ADV001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workflowType").value("ACCOUNT_OPENING"))
                .andExpect(jsonPath("$.status").value("MAPPED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andReturn();

        Long workflowId = readWorkflowId(createResult);

        WorkflowRequestEntity workflowRequest = workflowRequestRepository.findById(workflowId).orElseThrow();
        assertThat(workflowRequest.getWorkflowType()).isEqualTo(WorkflowType.ACCOUNT_OPENING);
        assertThat(workflowRequest.getSourceSystem()).isEqualTo("DIGITAL_CHANNEL");
        assertThat(workflowRequest.getStatus()).isEqualTo(WorkflowStatus.MAPPED);
        assertThat(workflowRequest.getOriginalPayload()).contains("Alice Chen");
        assertThat(workflowRequest.getMappedPayload()).contains("customer_id");
        assertThat(workflowRequest.getMappedPayload()).contains("SAV001");
        assertThat(workflowRequest.getFailureReason()).isNull();

        List<AuditLogEntity> auditLogs =
                auditLogRepository.findByWorkflowRequest_IdOrderByCreatedAtAsc(workflowId);
        assertThat(auditLogs)
                .extracting(AuditLogEntity::getEventType)
                .containsExactly(
                        AuditEventType.REQUEST_RECEIVED,
                        AuditEventType.VALIDATION_PASSED,
                        AuditEventType.PAYLOAD_MAPPED,
                        AuditEventType.KAFKA_EVENT_PUBLISHED
                );

        mockMvc.perform(get("/api/workflows/{workflowId}", workflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value(workflowId))
                .andExpect(jsonPath("$.status").value("MAPPED"))
                .andExpect(jsonPath("$.mappedPayload").value(org.hamcrest.Matchers.containsString("SAV001")));

        mockMvc.perform(get("/api/workflows/{workflowId}/audit-logs", workflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].eventType").value("REQUEST_RECEIVED"))
                .andExpect(jsonPath("$[3].eventType").value("KAFKA_EVENT_PUBLISHED"));
    }

    @Test
    void retryEndpointRecordsRetryAttemptAndRepublishesFailedWorkflow() throws Exception {
        WorkflowRequestEntity failedWorkflow = saveFailedMappedWorkflow();

        mockMvc.perform(post("/api/workflows/{workflowId}/retry", failedWorkflow.getId()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.workflowId").value(failedWorkflow.getId()))
                .andExpect(jsonPath("$.status").value("RETRYING"))
                .andExpect(jsonPath("$.message").value("Retry requested for failed workflow"));

        WorkflowRequestEntity retriedWorkflow = workflowRequestRepository.findById(failedWorkflow.getId()).orElseThrow();
        assertThat(retriedWorkflow.getStatus()).isEqualTo(WorkflowStatus.RETRYING);
        assertThat(retriedWorkflow.getRetryCount()).isEqualTo(1);
        assertThat(retriedWorkflow.getFailureReason()).isNull();

        List<RetryAttemptEntity> retryAttempts =
                retryAttemptRepository.findByWorkflowRequest_IdOrderByAttemptNumberAsc(failedWorkflow.getId());
        assertThat(retryAttempts).hasSize(1);
        assertThat(retryAttempts.getFirst().getAttemptNumber()).isEqualTo(1);
        assertThat(retryAttempts.getFirst().getStatus()).isEqualTo(RetryAttemptStatus.EVENT_PUBLISHED);
        assertThat(retryAttempts.getFirst().getFailureReason())
                .isEqualTo("Core banking rejected the account-opening request");

        List<AuditLogEntity> auditLogs =
                auditLogRepository.findByWorkflowRequest_IdOrderByCreatedAtAsc(failedWorkflow.getId());
        assertThat(auditLogs)
                .extracting(AuditLogEntity::getEventType)
                .containsExactly(
                        AuditEventType.RETRY_REQUESTED,
                        AuditEventType.KAFKA_EVENT_PUBLISHED
                );
    }

    @Test
    void retryEndpointRejectsCompletedWorkflow() throws Exception {
        WorkflowRequestEntity completedWorkflow = saveCompletedWorkflow();

        mockMvc.perform(post("/api/workflows/{workflowId}/retry", completedWorkflow.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Workflow cannot be retried"))
                .andExpect(jsonPath("$.message").value(
                        "Workflow " + completedWorkflow.getId() + " cannot be retried from status COMPLETED"
                ));

        assertThat(retryAttemptRepository.findByWorkflowRequest_IdOrderByAttemptNumberAsc(completedWorkflow.getId()))
                .isEmpty();
    }

    private Long readWorkflowId(MvcResult result) throws Exception {
        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        return responseBody.get("workflowId").asLong();
    }

    private WorkflowRequestEntity saveFailedMappedWorkflow() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.FAILED);
        workflowRequest.setCorrelationId("integration-retry-failed");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "FAIL-123",
                  "fullName": "Failure Test"
                }
                """);
        workflowRequest.setMappedPayload("""
                {
                  "customer_id": "FAIL-123",
                  "customer_name": "Failure Test",
                  "product_code": "SAV001",
                  "advisor_id": "ADV001"
                }
                """);
        workflowRequest.setFailureReason("Core banking rejected the account-opening request");

        return workflowRequestRepository.saveAndFlush(workflowRequest);
    }

    private WorkflowRequestEntity saveCompletedWorkflow() {
        WorkflowRequestEntity workflowRequest = new WorkflowRequestEntity();
        workflowRequest.setWorkflowType(WorkflowType.ACCOUNT_OPENING);
        workflowRequest.setSourceSystem("DIGITAL_CHANNEL");
        workflowRequest.setStatus(WorkflowStatus.COMPLETED);
        workflowRequest.setCorrelationId("integration-completed");
        workflowRequest.setOriginalPayload("""
                {
                  "clientId": "C999",
                  "fullName": "Completed Test"
                }
                """);
        workflowRequest.setMappedPayload("""
                {
                  "customer_id": "C999",
                  "customer_name": "Completed Test",
                  "product_code": "SAV001",
                  "advisor_id": "ADV001"
                }
                """);

        return workflowRequestRepository.saveAndFlush(workflowRequest);
    }
}
