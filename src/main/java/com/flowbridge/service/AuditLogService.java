package com.flowbridge.service;

import com.flowbridge.dto.AuditLogResponse;
import com.flowbridge.entity.AuditLogEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void writeAuditLog(
            WorkflowRequestEntity workflowRequest,
            AuditEventType eventType,
            String message,
            String metadata
    ) {
        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setWorkflowRequest(workflowRequest);
        auditLog.setCorrelationId(workflowRequest.getCorrelationId());
        auditLog.setEventType(eventType);
        auditLog.setMessage(message);
        auditLog.setMetadata(metadata);

        auditLogRepository.save(auditLog);
        log.info(
                "Audit event {} written for workflow {} with correlationId {}",
                eventType,
                workflowRequest.getId(),
                workflowRequest.getCorrelationId()
        );
    }

    public List<AuditLogResponse> getAuditLogsForWorkflow(Long workflowId) {
        return auditLogRepository.findByWorkflowRequest_IdOrderByCreatedAtAsc(workflowId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditLogResponse toResponse(AuditLogEntity auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getWorkflowRequest().getId(),
                auditLog.getCorrelationId(),
                auditLog.getEventType(),
                auditLog.getMessage(),
                auditLog.getMetadata(),
                auditLog.getCreatedAt()
        );
    }
}
