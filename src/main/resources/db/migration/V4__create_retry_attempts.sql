CREATE TABLE retry_attempts (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflow_requests(id),
    attempt_number INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_retry_attempts_workflow_id_attempt_number
    ON retry_attempts(workflow_id, attempt_number);
