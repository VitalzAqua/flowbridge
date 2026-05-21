CREATE TABLE workflow_requests (
    id BIGSERIAL PRIMARY KEY,
    workflow_type VARCHAR(100) NOT NULL,
    source_system VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL UNIQUE,
    idempotency_key VARCHAR(150),
    original_payload JSONB NOT NULL,
    mapped_payload JSONB,
    failure_reason TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workflow_requests_status ON workflow_requests(status);
CREATE INDEX idx_workflow_requests_correlation_id ON workflow_requests(correlation_id);
