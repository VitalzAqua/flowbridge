package com.flowbridge.repository;

import com.flowbridge.entity.OutboxEventEntity;
import com.flowbridge.enums.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findTop20ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    List<OutboxEventEntity> findByWorkflowRequest_IdOrderByCreatedAtAsc(Long workflowId);
}
