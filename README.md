# FlowBridge Lite

FlowBridge Lite is a Java 21 / Spring Boot backend that simulates the integration layer behind an enterprise banking workflow. It receives account-opening requests, validates and maps payloads, persists workflow state in PostgreSQL, queues Kafka events through a transactional outbox, processes downstream work asynchronously, records audit logs, saves external system responses, and supports retry for failed workflows.

The project is intentionally focused on backend workflow fundamentals rather than building a full banking platform.

## Tech Stack

- Java 21 and Spring Boot 3 for the backend application.
- Spring Web for REST APIs.
- Spring Data JPA and Hibernate for persistence.
- PostgreSQL and JSONB for workflow, payload, audit, outbox, retry, and external response storage.
- Flyway for versioned database migrations.
- Apache Kafka for asynchronous workflow processing.
- Docker Compose for local PostgreSQL, Kafka, and app startup.
- Spring Boot Actuator for deployment health checks.
- JUnit 5, Mockito, Spring Boot Test, and Testcontainers for testing.

## Architecture

```text
Client / Digital Channel
  ↓
WorkflowController
  ↓
WorkflowService
  ↓
Validation + MappingService
  ↓
PostgreSQL workflow_requests + audit_logs
  ↓
outbox_events
  ↓
OutboxEventPublisher → Kafka topic: flowbridge.workflow.events
  ↓
WorkflowEventConsumer
  ↓
WorkflowProcessingService
  ↓
MockCoreBankingService
  ↓
external_system_responses + final workflow status + audit logs
```

The app follows a layered structure:

- Controllers handle HTTP requests and responses.
- Services contain business workflow logic.
- Repositories handle database access.
- DTOs represent API contracts and payloads.
- Entities represent persisted database rows.
- Outbox and Kafka services publish and consume workflow events safely.

## Workflow Summary

Happy path:

```text
RECEIVED -> VALIDATED -> MAPPED -> PROCESSING -> COMPLETED
```

Failure path:

```text
RECEIVED -> VALIDATED -> MAPPED -> PROCESSING -> FAILED
```

Retry path:

```text
FAILED -> RETRYING -> PROCESSING -> COMPLETED
```

or:

```text
FAILED -> RETRYING -> PROCESSING -> FAILED
```

The workflow stores an internal `idempotencyKey` and includes it in Kafka events. The consumer locks the workflow row, verifies the event key, and only processes workflows in `MAPPED` or `RETRYING`; duplicate or stale events are skipped and audited.

## API Endpoints

Create an account-opening workflow:

```http
POST /api/workflows/account-opening
```

Request:

```json
{
  "clientId": "C123",
  "fullName": "Alice Chen",
  "dateOfBirth": "2001-05-01",
  "accountType": "SAVINGS",
  "advisorCode": "ADV001"
}
```

Get workflow details:

```http
GET /api/workflows/{workflowId}
```

Get workflow audit logs:

```http
GET /api/workflows/{workflowId}/audit-logs
```

Retry a failed workflow:

```http
POST /api/workflows/{workflowId}/retry
```

Retry is only allowed when the workflow is `FAILED`, has not exceeded the max retry count, and has a mapped payload that can be reprocessed.

## Local Docker Run

Build and start PostgreSQL, Kafka, and the Spring Boot app:

```bash
docker compose up --build
```

The app runs at:

```text
http://localhost:8080
```

Health check endpoint:

```text
http://localhost:8080/actuator/health
```

PostgreSQL is exposed locally on:

```text
localhost:5433
```

Kafka is exposed locally on:

```text
localhost:9092
```

Stop the local stack:

```bash
docker compose down
```

Remove local database data and containers:

```bash
docker compose down -v
```

## Local Development Run

If you want to run the app from your IDE or terminal instead of inside Docker, start only the dependencies:

```bash
docker compose up -d postgres kafka
```

Then run:

```bash
./mvnw spring-boot:run
```

## AWS Deployment Target

The recommended AWS deployment path is intentionally practical:

```text
Internet
  ↓
Application Load Balancer
  ↓
ECS Fargate service running the FlowBridge Docker image
  ↓
RDS PostgreSQL

ECS Fargate task
  ↓
Kafka running in Docker on a small EC2 instance

ECS task logs
  ↓
CloudWatch Logs
```

Use ECS Fargate for the Spring Boot app because FlowBridge is already packaged as a container. Fargate lets AWS run the container without requiring you to patch or administer an application EC2 instance. That keeps the project focused on backend deployment architecture: container image, task definition, health checks, environment variables, IAM roles, service networking, logs, and managed database access.

The app has an `aws` Spring profile for ECS:

```bash
SPRING_PROFILES_ACTIVE=aws
SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/flowbridge
SPRING_DATASOURCE_USERNAME=<database-user>
SPRING_DATASOURCE_PASSWORD=<database-password>
SPRING_KAFKA_BOOTSTRAP_SERVERS=<kafka-ec2-private-dns>:9092
```

Optional deployment variables:

```bash
SERVER_PORT=8080
FLOWBRIDGE_KAFKA_WORKFLOW_EVENTS_TOPIC=flowbridge.workflow.events
FLOWBRIDGE_KAFKA_CONSUMER_GROUP_ID=flowbridge-workflow-consumer
FLOWBRIDGE_OUTBOX_PUBLISHER_ENABLED=true
FLOWBRIDGE_OUTBOX_PUBLISHER_FIXED_DELAY_MS=5000
```

For ECS and the Application Load Balancer, configure the target group health check path as:

```text
/actuator/health
```

The Docker image also includes a container-level health check that calls the same endpoint. In AWS, the database password should be passed through SSM Parameter Store or Secrets Manager rather than hard-coded in the task definition.

AWS deployment files live in:

```text
deploy/aws
```

Start with:

```text
deploy/aws/README.md
```

## API Smoke Test

Create a successful workflow:

```bash
curl -X POST http://localhost:8080/api/workflows/account-opening \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "C123",
    "fullName": "Alice Chen",
    "dateOfBirth": "2001-05-01",
    "accountType": "SAVINGS",
    "advisorCode": "ADV001"
  }'
```

Check the workflow after Kafka processes it:

```bash
curl http://localhost:8080/api/workflows/<workflowId>
```

Expected final status:

```text
COMPLETED
```

Check audit logs:

```bash
curl http://localhost:8080/api/workflows/<workflowId>/audit-logs
```

Expected audit events include:

```text
REQUEST_RECEIVED
VALIDATION_PASSED
PAYLOAD_MAPPED
KAFKA_EVENT_QUEUED
KAFKA_EVENT_PUBLISHED
PROCESSING_STARTED
EXTERNAL_SYSTEM_CALL_STARTED
EXTERNAL_SYSTEM_CALL_SUCCEEDED
WORKFLOW_COMPLETED
```

Create a downstream failure:

```bash
curl -X POST http://localhost:8080/api/workflows/account-opening \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "FAIL-123",
    "fullName": "Failure Test",
    "dateOfBirth": "2001-05-01",
    "accountType": "SAVINGS",
    "advisorCode": "ADV001"
  }'
```

Retry a failed workflow:

```bash
curl -X POST http://localhost:8080/api/workflows/<workflowId>/retry
```

The retry request moves the workflow to `RETRYING`, increments `retryCount`, records a retry attempt, queues a new outbox event, and lets the Kafka consumer process the mapped payload again.

## Database Tables

- `workflow_requests`: stores workflow type, status, original payload, mapped payload, failure reason, retry count, correlation ID, internal idempotency key, and timestamps.
- `audit_logs`: stores important workflow events such as request received, payload mapped, Kafka event queued/published, processing started, workflow completed, and workflow failed.
- `external_system_responses`: stores the response returned by the mock core banking system.
- `retry_attempts`: stores retry attempt number, status, previous failure reason, and creation timestamp.
- `outbox_events`: stores pending Kafka events in the same database transaction as workflow changes, then publishes them separately.

All tables are created through Flyway migrations in:

```text
src/main/resources/db/migration
```

## Testing

Run the full test suite:

```bash
./mvnw test
```

Run the API/database integration test:

```bash
./mvnw -Dtest=AccountOpeningWorkflowIntegrationTest test
```

Some tests use Testcontainers, so Docker must be running.

The tests cover:

- Payload mapping rules.
- Workflow status transition validation.
- Workflow creation and audit logging.
- Internal idempotency key generation.
- Outbox event creation and publishing.
- Kafka event producer and consumer delegation.
- Mock core banking success and failure behavior.
- Retry service rules and retry outbox behavior.
- Duplicate or stale Kafka event handling.
- Repository persistence with PostgreSQL Testcontainers.
- API integration through `MockMvc`.

## Learning Outcomes

This project demonstrates:

- Layered Spring Boot architecture.
- DTO/entity separation.
- PostgreSQL persistence with Flyway-managed schema.
- JSONB payload storage.
- Workflow state transitions.
- Correlation IDs for traceability.
- Audit logging for operational visibility.
- Kafka producer/consumer async processing.
- Downstream integration simulation.
- Retry, transactional outbox, and internal Kafka idempotency patterns.
- Unit, repository, and integration testing.
- Docker-based local development.
- AWS-ready health checks and environment-based runtime configuration.

## Known Limitations

- `MockCoreBankingService` is an in-app simulator, not a real external service.
- Retry currently requeues the same mapped payload, so permanent business-rule failures will fail again.
- Idempotency is implemented internally for workflow/Kafka processing, not as a full HTTP `Idempotency-Key` API.
- Swagger/OpenAPI is intentionally not included because the core backend workflow is the focus.
- There is no authentication, frontend, complete cloud deployment, Redis, Kubernetes, or multiple microservices.

## Future Improvements

- Add richer outbox retry scheduling and operational visibility for failed outbox events.
- Move mock core banking into a separate service and call it over HTTP.
- Add full API idempotency using an `Idempotency-Key` header.
- Add CI with GitHub Actions.
- Deploy the containerized Spring Boot app to ECS Fargate with RDS PostgreSQL, CloudWatch Logs, IAM roles, and EC2-hosted Kafka.
- Add Swagger/OpenAPI later if API documentation becomes a priority.
- Add security only after the backend workflow is complete.
