package com.flowbridge.repository;

import com.flowbridge.entity.WorkflowRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowRequestRepository extends JpaRepository<WorkflowRequestEntity, Long> {

    Optional<WorkflowRequestEntity> findByCorrelationId(String correlationId);
}
