package com.flowbridge.service;

import com.flowbridge.dto.AuditLogResponse;
import com.flowbridge.entity.AuditLogEntity;
import com.flowbridge.entity.WorkflowRequestEntity;
import com.flowbridge.enums.AuditEventType;
import com.flowbridge.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

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
