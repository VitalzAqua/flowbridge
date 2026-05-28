CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflow_requests(id),
    event_type VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

CREATE INDEX idx_outbox_events_status_created_at
    ON outbox_events(status, created_at);

CREATE INDEX idx_outbox_events_workflow_id
    ON outbox_events(workflow_id);

CREATE INDEX idx_outbox_events_idempotency_key
    ON outbox_events(idempotency_key);
