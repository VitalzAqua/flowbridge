package com.flowbridge.repository;

import com.flowbridge.entity.WorkflowRequestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkflowRequestRepository extends JpaRepository<WorkflowRequestEntity, Long> {

    Optional<WorkflowRequestEntity> findByCorrelationId(String correlationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select workflowRequest from WorkflowRequestEntity workflowRequest where workflowRequest.id = :workflowId")
    Optional<WorkflowRequestEntity> findByIdForUpdate(@Param("workflowId") Long workflowId);
}
