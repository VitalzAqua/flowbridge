CREATE TABLE external_system_responses (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflow_requests(id),
    external_reference_id VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    error_code VARCHAR(100),
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_external_system_responses_workflow_id ON external_system_responses(workflow_id);
CREATE INDEX idx_external_system_responses_workflow_id_status ON external_system_responses(workflow_id, status);
