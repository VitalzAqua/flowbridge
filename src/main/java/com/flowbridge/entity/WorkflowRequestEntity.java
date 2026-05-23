package com.flowbridge.entity;

import com.flowbridge.enums.WorkflowStatus;
import com.flowbridge.enums.WorkflowType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "workflow_requests")
public class WorkflowRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", nullable = false, length = 100)
    private WorkflowType workflowType;

    @Setter
    @Column(name = "source_system", nullable = false, length = 100)
    private String sourceSystem;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private WorkflowStatus status;

    @Setter
    @Column(name = "correlation_id", nullable = false, unique = true, length = 100)
    private String correlationId;

    @Setter
    @Column(name = "idempotency_key", length = 150)
    private String idempotencyKey;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "original_payload", nullable = false, columnDefinition = "jsonb")
    private String originalPayload;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapped_payload", columnDefinition = "jsonb")
    private String mappedPayload;

    @Setter
    @Column(name = "failure_reason")
    private String failureReason;

    @Setter
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public WorkflowRequestEntity() {
    }

}
