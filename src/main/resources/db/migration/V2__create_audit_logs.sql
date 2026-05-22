CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflow_requests(id),
    correlation_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_workflow_id ON audit_logs(workflow_id);
CREATE INDEX idx_audit_logs_correlation_id ON audit_logs(correlation_id);
