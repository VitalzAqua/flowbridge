package com.flowbridge.repository;

import com.flowbridge.entity.RetryAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RetryAttemptRepository extends JpaRepository<RetryAttemptEntity, Long> {

    List<RetryAttemptEntity> findByWorkflowRequest_IdOrderByAttemptNumberAsc(Long workflowId);
}
