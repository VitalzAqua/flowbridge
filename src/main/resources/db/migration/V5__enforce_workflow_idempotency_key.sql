UPDATE workflow_requests
SET idempotency_key = workflow_type || ':' || correlation_id
WHERE idempotency_key IS NULL;

ALTER TABLE workflow_requests
    ALTER COLUMN idempotency_key SET NOT NULL;

CREATE UNIQUE INDEX idx_workflow_requests_idempotency_key
    ON workflow_requests(idempotency_key);
