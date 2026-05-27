package com.flowbridge.repository;

import com.flowbridge.entity.ExternalSystemResponseEntity;
import com.flowbridge.enums.ExternalSystemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalSystemResponseRepository extends JpaRepository<ExternalSystemResponseEntity, Long> {

    boolean existsByWorkflowRequest_IdAndStatus(Long workflowId, ExternalSystemStatus status);
}
