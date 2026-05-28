# FlowBridge Lite

FlowBridge Lite is a Java 21 / Spring Boot backend that simulates the integration layer behind an enterprise banking workflow. It receives account-opening requests, validates and maps payloads, persists workflow state in PostgreSQL, publishes Kafka events, processes downstream work asynchronously, records audit logs, saves external system responses, and supports retry for failed workflows.

The project is intentionally focused on backend workflow fundamentals rather than building a full banking platform.

## Tech Stack

- Java 21 and Spring Boot 3 for the backend application.
- Spring Web for REST APIs.
- Spring Data JPA and Hibernate for persistence.
- PostgreSQL and JSONB for workflow, payload, audit, retry, and external response storage.
- Flyway for versioned database migrations.
- Apache Kafka for asynchronous workflow processing.
- Docker Compose for local PostgreSQL, Kafka, and app startup.
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
WorkflowEventProducer
  ↓
Kafka topic: flowbridge.workflow.events
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
- Kafka classes publish and consume workflow events.

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

The Kafka consumer includes a basic idempotency guard: if a workflow is already `COMPLETED` or already has a successful external response, duplicate events are skipped and audited instead of calling the downstream mock service again.

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

## Database Tables

- `workflow_requests`: stores workflow type, status, original payload, mapped payload, failure reason, retry count, correlation ID, and timestamps.
- `audit_logs`: stores important workflow events such as request received, payload mapped, Kafka event published, processing started, workflow completed, and workflow failed.
- `external_system_responses`: stores the response returned by the mock core banking system.
- `retry_attempts`: stores retry attempt number, status, previous failure reason, and creation timestamp.

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
- Kafka event producer and consumer delegation.
- Mock core banking success and failure behavior.
- Retry service rules.
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
- Retry and basic idempotency patterns.
- Unit, repository, and integration testing.
- Docker-based local development.

## Known Limitations

- `MockCoreBankingService` is an in-app simulator, not a real external service.
- Retry currently republishes the same mapped payload, so permanent business-rule failures will fail again.
- Idempotency is implemented for Kafka consumer side effects, not as a full HTTP `Idempotency-Key` API.
- Swagger/OpenAPI is intentionally not included because the core backend workflow is the focus.
- There is no authentication, frontend, cloud deployment, Redis, Kubernetes, or multiple microservices.

## Future Improvements

- Add an outbox table to make database commits and Kafka publishing more reliable.
- Move mock core banking into a separate service and call it over HTTP.
- Add full API idempotency using an `Idempotency-Key` header.
- Add CI with GitHub Actions.
- Add Swagger/OpenAPI later if API documentation becomes a priority.
- Add security only after the backend workflow is complete.

## Resume Summary

```text
FlowBridge Lite — Banking Workflow Integration Simulator
Java, Spring Boot, PostgreSQL, Kafka, Docker, JUnit, Mockito, Testcontainers, Flyway

- Built a Spring Boot banking workflow simulator that processes account-opening requests through validation, payload mapping, asynchronous Kafka dispatch, mock downstream integration, retry handling, and audit logging.
- Designed PostgreSQL/Flyway-backed workflow models for state tracking, JSONB payload storage, retry attempts, downstream responses, correlation IDs, and traceable audit events.
- Implemented Kafka producer/consumer processing with workflow state transitions, idempotency checks, structured logging, and failure recovery for enterprise-style integration workflows.
- Added JUnit/Mockito unit tests and Testcontainers integration tests to validate mapping logic, workflow transitions, persistence, retry behavior, and database-backed API flows.
```
