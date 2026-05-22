package com.flowbridge.repository;

import com.flowbridge.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findByWorkflowRequest_IdOrderByCreatedAtAsc(Long workflowId);
}
