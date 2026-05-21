# FlowBridge Lite — Banking Workflow Integration Simulator

## 1. Project Purpose

FlowBridge Lite is a focused Java/Spring Boot backend project that simulates the integration layer behind a bank, insurance company, or enterprise financial system.

The goal is **not** to build a full banking platform. The goal is to build a realistic backend workflow system that demonstrates how enterprise systems receive requests, validate them, transform data formats, persist workflow state, publish asynchronous events, process downstream integrations, handle failures, support retries, and maintain auditability.

This project is designed to help the developer build real backend engineering skills for roles such as:

- Java Backend Developer
- Spring Boot Developer
- Application Developer
- Integration Developer
- API Developer
- Banking / Insurance Software Developer
- Consulting Technology Analyst / Developer
- Production Support Developer with Java, SQL, API, and workflow exposure

The project should prioritize **understanding, clean architecture, and interview explainability** over feature count.

---

## 2. Big Picture System Idea

In real banks and enterprise financial systems, customer-facing websites or employee portals usually do not write directly into one giant database.

Instead, large organizations often have many internal systems, such as:

- Customer information system
- Core banking/account system
- KYC/compliance system
- Credit card system
- Loan system
- Notification system
- Audit/reporting system

These systems may use different databases, APIs, field names, payload formats, and business rules.

FlowBridge Lite represents the middleware/integration layer that sits between a digital channel and downstream systems.

### Main workflow for MVP

```text
Client / Digital Channel
    ↓
REST API receives account-opening request
    ↓
Request validation
    ↓
Payload mapping / transformation
    ↓
Workflow state saved in PostgreSQL
    ↓
Audit logs written
    ↓
Kafka event published
    ↓
Kafka consumer processes event
    ↓
Mock core banking system is called
    ↓
Workflow status updated
    ↓
External response saved
    ↓
Audit logs updated
    ↓
Failed workflows can be retried
```

The first workflow will be:

```text
Account Opening Workflow
```

Future workflows can be added later, but the MVP should focus only on account opening.

---

## 3. Core Learning Goals

This project should teach and demonstrate:

- Java backend development
- Spring Boot REST API design
- Enterprise-style layered architecture
- DTOs, entities, services, repositories
- Request validation
- Payload transformation/mapping
- PostgreSQL persistence
- JSONB storage
- Workflow status tracking
- Audit logging
- State machine thinking
- Correlation IDs
- Idempotency basics
- Kafka asynchronous processing
- Downstream/external system integration
- Error handling and retry logic
- Structured logging
- Flyway database migrations
- Unit testing
- Integration testing with Testcontainers
- Docker-based local development
- Optional cloud/deployment thinking later

---

## 4. Main Tech Stack

### Required MVP stack

| Tool / Framework | Purpose | Why It Is Used |
|---|---|---|
| Java 21 | Main programming language | Modern long-term Java version used in enterprise backend systems |
| Spring Boot 3 | Backend framework | Standard Java backend framework for REST APIs, dependency injection, configuration, and enterprise apps |
| Spring Web | REST API layer | Used to expose HTTP endpoints such as POST workflow, GET workflow, retry workflow |
| Spring Data JPA | Database access layer | Provides repository abstraction and ORM support for PostgreSQL entities |
| Hibernate | JPA implementation | Converts Java entities into database operations |
| PostgreSQL | Relational database | Realistic enterprise-grade database for workflow state, audit logs, retry attempts, and downstream responses |
| PostgreSQL JSONB | Store request/response payloads | Useful for storing original and mapped payloads without over-normalizing every field |
| Flyway | Database migrations | Tracks schema changes using versioned SQL scripts instead of relying on Hibernate auto-DDL |
| Apache Kafka | Async event processing | Decouples request intake from downstream processing, similar to enterprise event-driven systems |
| Docker | Local containerization | Makes PostgreSQL, Kafka, and the app easier to run consistently |
| Docker Compose | Local multi-service setup | Runs app, database, and Kafka together during development |
| JUnit 5 | Unit/integration testing | Standard testing framework for Java applications |
| Mockito | Mocking dependencies in tests | Allows testing service logic without real database or Kafka calls |
| Testcontainers | Real integration testing | Runs PostgreSQL/Kafka in containers during tests to verify real behavior |
| Maven | Build/dependency tool | Common Java build tool used to manage dependencies, tests, and packaging |
| SLF4J + Logback | Structured logging | Standard Java logging approach for debugging, monitoring, and traceability |
| Swagger/OpenAPI | API documentation | Makes endpoints easy to inspect and test through a generated UI |

### Optional later additions

| Tool / Feature | Purpose | When to Add |
|---|---|---|
| Spring Security + JWT | Authentication/authorization | Add only after core backend works |
| Redis | Cache or idempotency helper | Add only if there is a clear use case |
| GitHub Actions | CI/CD pipeline | Add after tests exist |
| AWS ECS / Elastic Beanstalk | Cloud deployment | Add after Docker Compose works locally |
| AWS RDS | Managed PostgreSQL | Add if deploying to AWS |
| AWS MSK / alternative Kafka hosting | Managed Kafka | Optional; may be expensive |
| Kubernetes manifests | Container orchestration | Add only after MVP and Docker are mature |

Do not start with Kubernetes, Redis, AWS, or microservices. Build the core backend first.

---

## 5. MVP Functional Scope

### MVP Phase 1: Synchronous workflow without Kafka

The first milestone should be:

```text
POST /api/workflows/account-opening
    ↓
Validate request
    ↓
Save workflow with RECEIVED status
    ↓
Mark workflow VALIDATED
    ↓
Map payload
    ↓
Save mapped payload
    ↓
Mark workflow MAPPED
    ↓
Write audit logs
    ↓
Return workflow ID and status
```

No Kafka yet in Phase 1.

This milestone proves the basic layered architecture, validation, mapping, database persistence, status tracking, and audit logging.

### MVP Phase 2: Add Kafka and async processing

After Phase 1 works:

```text
Mapped workflow
    ↓
Publish Kafka event
    ↓
Consumer receives event
    ↓
Mark workflow PROCESSING
    ↓
Call mock core banking system
    ↓
Save external response
    ↓
Mark COMPLETED or FAILED
    ↓
Write audit logs
```

### MVP Phase 3: Add retry handling

Add:

```http
POST /api/workflows/{id}/retry
```

Retry should only be allowed for failed workflows.

---

## 6. REST API Design

### 6.1 Create account-opening workflow

```http
POST /api/workflows/account-opening
```

Example request:

```json
{
  "clientId": "C123",
  "fullName": "Alice Chen",
  "dateOfBirth": "2001-05-01",
  "accountType": "SAVINGS",
  "advisorCode": "ADV001"
}
```

Expected response:

```json
{
  "workflowId": 1001,
  "workflowType": "ACCOUNT_OPENING",
  "status": "MAPPED",
  "correlationId": "FLOW-20260520-0001",
  "message": "Account opening workflow received and mapped successfully"
}
```

### 6.2 Get workflow by ID

```http
GET /api/workflows/{id}
```

Returns current workflow details, including status, failure reason, retry count, original payload, mapped payload, timestamps, and correlation ID.

### 6.3 Get audit logs for workflow

```http
GET /api/workflows/{id}/audit-logs
```

Returns audit history for the workflow.

### 6.4 Retry failed workflow

```http
POST /api/workflows/{id}/retry
```

Only allowed when workflow status is `FAILED`.

Expected behavior:

- Check workflow exists
- Check current status is `FAILED`
- Increment retry count
- Create retry attempt record
- Write audit log
- Set status to `RETRYING`
- Publish Kafka event again

---

## 7. Validation Rules

Use Spring validation annotations where possible.

Example request DTO:

```java
public class AccountOpeningRequest {
    @NotBlank
    private String clientId;

    @NotBlank
    private String fullName;

    @NotNull
    private LocalDate dateOfBirth;

    @NotNull
    private AccountType accountType;

    private String advisorCode;
}
```

Allowed account types:

```java
public enum AccountType {
    SAVINGS,
    CHEQUING,
    TFSA
}
```

Validation goals:

- Reject missing `clientId`
- Reject missing `fullName`
- Reject missing `dateOfBirth`
- Reject unsupported `accountType`
- Decide whether `advisorCode` is optional or required

Recommended MVP design:

- `advisorCode` is optional
- If missing, map to `advisor_id: null`

Validation failure handling:

Option A:

- Return HTTP 400 and do not save workflow

Option B:

- Save workflow with status `FAILED_VALIDATION`

Recommended first implementation:

- For simple DTO validation errors, return HTTP 400
- For business-rule validation errors after intake, save workflow as `FAILED`

This keeps the first version simpler and cleaner.

---

## 8. Payload Mapping / Transformation

The source/client payload should be transformed into the fake core banking payload.

### Source payload

```json
{
  "clientId": "C123",
  "fullName": "Alice Chen",
  "accountType": "SAVINGS",
  "advisorCode": "ADV001"
}
```

### Target core banking payload

```json
{
  "customer_id": "C123",
  "customer_name": "Alice Chen",
  "product_code": "SAV001",
  "advisor_id": "ADV001"
}
```

### Mapping rules

| Source Field | Target Field | Rule |
|---|---|---|
| clientId | customer_id | Direct field rename |
| fullName | customer_name | Direct field rename |
| accountType | product_code | Convert account type to product code |
| advisorCode | advisor_id | Direct field rename |

### Account type mapping

| Account Type | Product Code |
|---|---|
| SAVINGS | SAV001 |
| CHEQUING | CHQ001 |
| TFSA | TFSA001 |

### Implementation approach

Start with a simple Java service:

```java
MappingService
```

Responsibilities:

- Accept `AccountOpeningRequest`
- Return `CoreBankingPayload`
- Convert account type to product code
- Keep mapping logic testable

Later optional enhancement:

- Store mapping rules in a `mapping_rules` database table
- Allow mapping behavior to be data-driven

Do not overcomplicate mapping in the first version.

---

## 9. Workflow Status Tracking

Each workflow should have a status.

### MVP statuses

```java
public enum WorkflowStatus {
    RECEIVED,
    VALIDATED,
    MAPPED,
    PROCESSING,
    COMPLETED,
    FAILED,
    RETRYING,
    CANCELLED
}
```

### Recommended status meaning

| Status | Meaning |
|---|---|
| RECEIVED | Request has been accepted by API |
| VALIDATED | Required fields/business rules passed |
| MAPPED | Source payload transformed into downstream format |
| PROCESSING | Kafka consumer is processing workflow |
| COMPLETED | Downstream system processed successfully |
| FAILED | Validation, mapping, Kafka, or downstream call failed |
| RETRYING | Failed workflow has been requested for retry |
| CANCELLED | Workflow was stopped manually or by business rule; optional for later |

---

## 10. State Machine Thinking

A workflow should not randomly jump between statuses.

Implement a small transition validator:

```java
WorkflowStatusTransitionValidator
```

Purpose:

- Prevent invalid status transitions
- Make workflow lifecycle explicit
- Teach enterprise workflow/state-machine thinking

### Allowed transitions

```text
RECEIVED -> VALIDATED
VALIDATED -> MAPPED
MAPPED -> PROCESSING
PROCESSING -> COMPLETED
PROCESSING -> FAILED
FAILED -> RETRYING
RETRYING -> PROCESSING
```

Optional later transitions:

```text
RECEIVED -> FAILED
VALIDATED -> FAILED
MAPPED -> FAILED
FAILED -> CANCELLED
```

### Invalid examples

```text
COMPLETED -> RETRYING
FAILED -> COMPLETED
PROCESSING -> VALIDATED
RECEIVED -> COMPLETED
```

If an invalid transition is attempted, throw:

```java
InvalidWorkflowStateException
```

Why this matters:

Enterprise systems often need strict lifecycle control. This teaches how to protect business workflows from inconsistent states.

---

## 11. Correlation IDs

Add a `correlationId` to every workflow.

Example:

```text
FLOW-20260520-0001
```

The correlation ID should appear in:

- workflow_requests table
- audit_logs table
- Kafka events
- structured logs
- API responses

Purpose:

- Helps trace a request across API, database, Kafka, consumer, and external system calls
- Simulates observability practices used in real distributed systems
- Makes debugging easier

Implementation:

Create a simple service:

```java
CorrelationIdService
```

It can generate IDs using:

- UUID
- timestamp + random suffix
- database sequence-based ID

Recommended MVP:

```java
UUID.randomUUID().toString()
```

Later, improve formatting if desired.

---

## 12. Idempotency

Idempotency means the same event/request can be processed more than once without causing duplicate side effects.

This matters because Kafka consumers may process the same message more than once.

### MVP idempotency approach

Add:

```text
idempotency_key
```

or use:

```text
correlation_id + workflow_type
```

For downstream processing:

- Before calling mock core banking, check whether an external success response already exists for this workflow
- If a workflow is already `COMPLETED`, do not call downstream again
- If duplicate Kafka event arrives, write audit log and skip processing

Example audit event:

```text
DUPLICATE_EVENT_SKIPPED
```

Purpose:

- Teaches safe async processing
- Prevents duplicate account creation simulation
- Demonstrates realistic backend reliability thinking

Do not make this too complex initially.

---

## 13. Kafka Async Processing

Kafka is used to decouple request intake from downstream processing.

### Why Kafka is used

Without Kafka:

```text
API request waits for downstream system call
```

With Kafka:

```text
API request saves workflow and publishes event
Consumer processes downstream work separately
```

This teaches:

- Event-driven architecture
- Producer/consumer design
- Async workflows
- Retry and failure handling
- Decoupling between request intake and processing

### Kafka event example

```json
{
  "workflowId": 1001,
  "workflowType": "ACCOUNT_OPENING",
  "eventType": "ACCOUNT_OPENING_MAPPED",
  "correlationId": "FLOW-20260520-0001",
  "timestamp": "2026-05-20T10:00:00Z"
}
```

### Kafka topic

Recommended topic name:

```text
flowbridge.workflow.events
```

### Producer responsibility

```java
WorkflowEventProducer
```

Responsibilities:

- Build workflow event
- Publish to Kafka topic
- Write audit log after successful publish
- Handle publish errors

### Consumer responsibility

```java
WorkflowEventConsumer
```

Responsibilities:

- Consume workflow event
- Load workflow from database
- Check idempotency
- Mark status as `PROCESSING`
- Call mock core banking service
- Save external system response
- Mark workflow as `COMPLETED` or `FAILED`
- Write audit logs

---

## 14. Mock Core Banking System

For MVP, create this as a service inside the same Spring Boot app:

```java
MockCoreBankingService
```

Purpose:

- Simulates a downstream internal banking system
- Avoids needing to build a second service at the beginning
- Allows testing success/failure paths

### Input

```java
CoreBankingPayload
```

### Success response

```json
{
  "externalReferenceId": "CORE-98765",
  "status": "SUCCESS",
  "message": "Account created successfully"
}
```

### Failure response

```json
{
  "status": "FAILED",
  "errorCode": "DUPLICATE_CUSTOMER",
  "message": "Customer already has this account type"
}
```

### Suggested fake business rules

| Rule | Result |
|---|---|
| clientId/customer_id starts with `FAIL` | Return failure |
| product_code is missing | Return failure |
| customer_id starts with `DUP` | Return duplicate customer failure |
| otherwise | Return success |

Later optional enhancement:

- Move mock core banking into a separate Spring Boot service
- Call it using REST with `RestClient` or `WebClient`

Do not start with a separate service. Keep MVP simple.

---

## 15. Retry Failed Workflow

Retry endpoint:

```http
POST /api/workflows/{id}/retry
```

Retry rules:

- Workflow must exist
- Workflow must be in `FAILED` status
- Completed workflows cannot be retried
- Processing workflows cannot be retried
- Retry count should increase
- Retry attempt should be recorded
- Audit log should be written
- Kafka event should be published again

### Retry flow

```text
FAILED workflow
    ↓
POST retry endpoint
    ↓
Validate workflow is retryable
    ↓
Increment retry count
    ↓
Create retry_attempt record
    ↓
Set status to RETRYING
    ↓
Write RETRY_REQUESTED audit log
    ↓
Publish Kafka event
    ↓
Consumer processes again
```

### Max retry count

Recommended:

```text
maxRetryCount = 3
```

If retry count exceeds limit, reject retry with clear error message.

Purpose:

- Teaches enterprise failure recovery
- Teaches workflow state control
- Teaches operational/backend support thinking

---

## 16. Audit Logging

Audit logs record important workflow steps.

This is important because banks and enterprise systems care about traceability.

### Audit log table fields

```text
id
workflow_id
correlation_id
event_type
message
metadata JSONB
created_at
```

### Audit event types

```java
public enum AuditEventType {
    REQUEST_RECEIVED,
    VALIDATION_PASSED,
    VALIDATION_FAILED,
    PAYLOAD_MAPPED,
    KAFKA_EVENT_PUBLISHED,
    PROCESSING_STARTED,
    EXTERNAL_SYSTEM_CALL_STARTED,
    EXTERNAL_SYSTEM_CALL_SUCCEEDED,
    EXTERNAL_SYSTEM_CALL_FAILED,
    DUPLICATE_EVENT_SKIPPED,
    RETRY_REQUESTED,
    WORKFLOW_COMPLETED,
    WORKFLOW_FAILED,
    INVALID_STATUS_TRANSITION
}
```

### Example audit log

```json
{
  "workflowId": 1001,
  "correlationId": "FLOW-20260520-0001",
  "eventType": "PAYLOAD_MAPPED",
  "message": "Mapped account-opening request to core banking payload",
  "metadata": {
    "sourceAccountType": "SAVINGS",
    "targetProductCode": "SAV001"
  },
  "createdAt": "2026-05-20T10:00:00Z"
}
```

Purpose:

- Provides traceability
- Helps debugging
- Makes project more realistic for banking/enterprise roles

---

## 17. Database Design

Use PostgreSQL with Flyway migrations.

Do not rely on Hibernate `ddl-auto=create` for final implementation.

Recommended development setting:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Flyway should create and update tables.

### 17.1 workflow_requests

```sql
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
```

### 17.2 audit_logs

```sql
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflow_requests(id),
    correlation_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 17.3 retry_attempts

```sql
CREATE TABLE retry_attempts (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflow_requests(id),
    attempt_number INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 17.4 external_system_responses

```sql
CREATE TABLE external_system_responses (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflow_requests(id),
    external_reference_id VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    error_code VARCHAR(100),
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 17.5 Optional later: mapping_rules

```sql
CREATE TABLE mapping_rules (
    id BIGSERIAL PRIMARY KEY,
    workflow_type VARCHAR(100) NOT NULL,
    source_field VARCHAR(100) NOT NULL,
    target_field VARCHAR(100) NOT NULL,
    source_value VARCHAR(100),
    target_value VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Do not implement mapping_rules in the first version unless the basic service mapping is already working.

---

## 18. Suggested Package Structure

```text
flowbridge-lite/
  src/main/java/com/flowbridge/
    FlowBridgeLiteApplication.java

    controller/
      WorkflowController.java

    dto/
      AccountOpeningRequest.java
      WorkflowResponse.java
      WorkflowDetailResponse.java
      CoreBankingPayload.java
      CoreBankingResponse.java
      AuditLogResponse.java
      ErrorResponse.java

    entity/
      WorkflowRequestEntity.java
      AuditLogEntity.java
      RetryAttemptEntity.java
      ExternalSystemResponseEntity.java

    enums/
      WorkflowType.java
      WorkflowStatus.java
      AccountType.java
      AuditEventType.java
      ExternalSystemStatus.java

    repository/
      WorkflowRequestRepository.java
      AuditLogRepository.java
      RetryAttemptRepository.java
      ExternalSystemResponseRepository.java

    service/
      WorkflowService.java
      MappingService.java
      AuditLogService.java
      RetryService.java
      MockCoreBankingService.java
      CorrelationIdService.java
      WorkflowStatusTransitionValidator.java
      IdempotencyService.java

    kafka/
      WorkflowEvent.java
      WorkflowEventProducer.java
      WorkflowEventConsumer.java

    exception/
      GlobalExceptionHandler.java
      WorkflowNotFoundException.java
      InvalidWorkflowStateException.java
      NonRetryableWorkflowException.java
      ExternalSystemException.java

    config/
      KafkaConfig.java
      JacksonConfig.java
      OpenApiConfig.java

  src/main/resources/
    application.yml
    db/migration/
      V1__create_workflow_tables.sql
      V2__create_audit_logs.sql
      V3__create_retry_attempts.sql
      V4__create_external_system_responses.sql

  src/test/java/com/flowbridge/
    service/
      MappingServiceTest.java
      WorkflowServiceTest.java
      RetryServiceTest.java
      MockCoreBankingServiceTest.java
      WorkflowStatusTransitionValidatorTest.java

    integration/
      AccountOpeningWorkflowIntegrationTest.java
      RetryWorkflowIntegrationTest.java
```

---

## 19. Layer Responsibilities

### Controller layer

Handles HTTP requests and responses.

Should not contain business logic.

Example:

```java
WorkflowController
```

Responsibilities:

- Receive API requests
- Validate request body using annotations
- Call service layer
- Return response DTOs

### Service layer

Contains business logic.

Examples:

```java
WorkflowService
MappingService
RetryService
AuditLogService
```

Responsibilities:

- Create workflows
- Validate business rules
- Map payloads
- Update workflow statuses
- Publish events
- Handle retries

### Repository layer

Handles database access.

Examples:

```java
WorkflowRequestRepository
AuditLogRepository
```

Responsibilities:

- Save entities
- Find workflows
- Query audit logs
- Check existing external responses

### Kafka layer

Handles event publishing and consuming.

Examples:

```java
WorkflowEventProducer
WorkflowEventConsumer
```

Responsibilities:

- Publish workflow events
- Consume workflow events
- Trigger async processing

### Exception layer

Converts exceptions into clean API responses.

Example:

```java
GlobalExceptionHandler
```

Responsibilities:

- Return HTTP 404 for workflow not found
- Return HTTP 400 for invalid state transitions
- Return HTTP 409 for non-retryable workflow states
- Return clear error messages

---

## 20. Error Handling Strategy

Use a global exception handler:

```java
@RestControllerAdvice
public class GlobalExceptionHandler
```

### Example error response

```json
{
  "timestamp": "2026-05-20T10:00:00Z",
  "status": 400,
  "error": "Invalid workflow state",
  "message": "Workflow 1001 cannot transition from COMPLETED to RETRYING",
  "path": "/api/workflows/1001/retry"
}
```

### Exception types

```java
WorkflowNotFoundException
InvalidWorkflowStateException
NonRetryableWorkflowException
ExternalSystemException
```

Purpose:

- Keeps controller clean
- Gives API users clear feedback
- Teaches production-style backend error handling

---

## 21. Structured Logging

Use SLF4J logging instead of `System.out.println`.

Example:

```java
log.info(
    "Workflow {} with correlationId {} transitioned from {} to {}",
    workflowId,
    correlationId,
    oldStatus,
    newStatus
);
```

Logging should include:

- workflow ID
- correlation ID
- status transition
- event type
- failure reason when applicable

Purpose:

- Makes debugging easier
- Simulates enterprise observability practices
- Helps production support style thinking

---

## 22. Flyway Migration Strategy

Use Flyway to manage database schema.

Example migration files:

```text
V1__create_workflow_requests.sql
V2__create_audit_logs.sql
V3__create_retry_attempts.sql
V4__create_external_system_responses.sql
V5__add_indexes.sql
```

Why Flyway is used:

- Tracks database changes over time
- Makes schema reproducible
- More realistic than letting Hibernate auto-create everything
- Common in enterprise Java projects

Recommended indexes:

```sql
CREATE INDEX idx_workflow_status ON workflow_requests(status);
CREATE INDEX idx_workflow_correlation_id ON workflow_requests(correlation_id);
CREATE INDEX idx_audit_logs_workflow_id ON audit_logs(workflow_id);
CREATE INDEX idx_external_responses_workflow_id ON external_system_responses(workflow_id);
```

---

## 23. Testing Strategy

### Unit tests

Use JUnit 5 and Mockito.

Minimum unit tests:

```text
MappingServiceTest
Validation/business rule tests
WorkflowServiceTest
RetryServiceTest
MockCoreBankingServiceTest
WorkflowStatusTransitionValidatorTest
IdempotencyServiceTest
```

Test examples:

- SAVINGS maps to SAV001
- CHEQUING maps to CHQ001
- TFSA maps to TFSA001
- Invalid status transition throws exception
- FAILED workflow can be retried
- COMPLETED workflow cannot be retried
- Mock core banking returns failure for customer ID starting with FAIL

### Integration tests

Use Testcontainers for PostgreSQL.

Minimum integration tests:

- POST account-opening creates workflow
- Valid request maps payload correctly
- Audit logs are created
- Failed mock external system marks workflow FAILED
- Retry failed workflow creates retry attempt
- Workflow status persists correctly

### Kafka integration tests

Add later if needed.

Use Kafka Testcontainers after basic Kafka works manually.

Why Testcontainers matters:

- Tests against real PostgreSQL instead of fake in-memory H2
- More realistic for backend development
- Strong resume signal

---

## 24. Docker Compose Setup

Use Docker Compose to run local dependencies.

Services:

- Spring Boot app
- PostgreSQL
- Kafka
- Kafka UI, optional

Recommended modern Kafka setup:

- Kafka KRaft mode if possible
- Or Kafka + Zookeeper if easier

Example service list:

```yaml
services:
  postgres:
    image: postgres:16

  kafka:
    image: bitnami/kafka:latest

  app:
    build: .
    depends_on:
      - postgres
      - kafka
```

Purpose:

- Makes local development reproducible
- Lets someone clone the repo and run the project easily
- Demonstrates container-based backend development

---

## 25. Swagger/OpenAPI

Add Swagger after MVP APIs work.

Dependency:

```text
springdoc-openapi-starter-webmvc-ui
```

Purpose:

- Auto-generates API documentation
- Makes endpoints testable in browser
- Helps interview/demo presentation
- Makes README easier to understand

Swagger endpoint usually:

```text
/swagger-ui.html
```

or:

```text
/swagger-ui/index.html
```

---

## 26. Recommended Build Order

### Milestone 1: Project setup

- Create Spring Boot project
- Add dependencies
- Connect PostgreSQL
- Add Flyway
- Create first migration
- Confirm app starts

### Milestone 2: Basic entities and repositories

- WorkflowRequestEntity
- AuditLogEntity
- Repositories
- Basic database persistence

### Milestone 3: Account-opening request API

- DTOs
- Controller
- WorkflowService
- Validation annotations
- Basic response DTO

### Milestone 4: Mapping service

- CoreBankingPayload DTO
- MappingService
- Account type to product code mapping
- Unit tests

### Milestone 5: Audit logging

- AuditLogService
- Write logs for request received, validation passed, payload mapped
- GET audit logs endpoint

### Milestone 6: Status transition validator

- WorkflowStatus enum
- Allowed transition rules
- InvalidWorkflowStateException
- Unit tests

### Milestone 7: Correlation ID and structured logging

- Add correlationId column
- Generate correlation ID on workflow creation
- Include in logs and audit logs

### Milestone 8: Kafka producer

- Add Kafka config
- Create WorkflowEvent
- Publish event after mapping
- Mark workflow as queued or keep status as MAPPED until consumer starts

### Milestone 9: Kafka consumer and mock core banking

- Consume workflow event
- Mark PROCESSING
- Call MockCoreBankingService
- Save response
- Mark COMPLETED or FAILED
- Write audit logs

### Milestone 10: Retry flow

- RetryAttemptEntity
- RetryService
- Retry endpoint
- Max retry count
- Republish Kafka event

### Milestone 11: Idempotency

- Prevent duplicate processing
- Skip already completed workflow events
- Audit duplicate skip

### Milestone 12: Integration tests

- Testcontainers PostgreSQL
- Test account-opening workflow persistence
- Test mapping persistence
- Test retry persistence

### Milestone 13: Docker Compose

- PostgreSQL
- Kafka
- App container
- README setup instructions

### Milestone 14: Swagger/OpenAPI

- Add API documentation
- Document endpoints and sample payloads

### Milestone 15: Polish README and resume bullets

- Architecture diagram
- Setup guide
- API examples
- Testing instructions
- Known limitations
- Future improvements

---

## 27. README Requirements

The final README should include:

### Project summary

Explain FlowBridge Lite in plain English.

### Architecture diagram

```text
Client
  ↓
WorkflowController
  ↓
WorkflowService
  ↓
Validation + Mapping
  ↓
PostgreSQL + Audit Logs
  ↓
Kafka Producer
  ↓
Kafka Topic
  ↓
Kafka Consumer
  ↓
Mock Core Banking Service
  ↓
Workflow Update + External Response + Audit Logs
```

### Tech stack section

Explain each tool briefly.

### API examples

Include curl examples for:

- Create account-opening workflow
- Get workflow
- Get audit logs
- Retry workflow

### Database schema summary

List main tables and purposes.

### Testing section

Explain how to run:

```bash
mvn test
```

### Docker section

Explain how to run:

```bash
docker compose up --build
```

### Learning outcomes

Explain what backend concepts this project demonstrates.

### Future improvements

Mention:

- Outbox pattern
- Separate mock core banking service
- Spring Security/JWT
- Redis
- AWS deployment
- Kubernetes

---

## 28. Future Advanced Enhancement: Outbox Pattern

Do not implement this in the MVP.

The outbox pattern solves this problem:

```text
Database save succeeds
Kafka publish fails
System is now inconsistent
```

Instead of publishing directly to Kafka inside the main workflow transaction, the app writes an event to an `outbox_events` table. A separate publisher then reads unpublished events and sends them to Kafka.

This improves reliability.

Possible table:

```sql
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);
```

Why it is valuable:

- Teaches reliable event publishing
- Common enterprise architecture pattern
- Good future resume/interview discussion topic

Add this only after the basic Kafka workflow works.

---

## 29. What Not To Build Initially

Do not build these at the beginning:

- Full customer database
- Real bank account database
- Real payment system
- Full frontend dashboard
- Multiple microservices
- Kubernetes
- Complex user roles
- Real KYC/document upload
- Complex mapping engine
- Real authentication system
- Event sourcing
- CQRS

These can distract from the main goal.

The project should stay focused on backend workflow integration fundamentals.

---

## 30. Suggested Final Resume Entry

```text
FlowBridge Lite — Banking Workflow Integration Simulator
Java, Spring Boot, PostgreSQL, Kafka, Docker, JUnit, Mockito, Testcontainers, Flyway

- Built a Java/Spring Boot banking workflow simulator that processes account-opening requests through validation, payload mapping, asynchronous Kafka dispatch, mock downstream integration, retry handling, and audit logging.
- Designed PostgreSQL/Flyway-backed workflow models for state tracking, JSONB payload storage, retry attempts, downstream responses, correlation IDs, and traceable audit events.
- Implemented Kafka producer/consumer processing with workflow state transitions, idempotency checks, structured logging, and failure recovery for enterprise-style integration workflows.
- Added JUnit/Mockito unit tests and Testcontainers integration tests to validate mapping logic, workflow transitions, persistence, retry behavior, and database-backed API flows.
```

---

## 31. Main Interview Explanation

A strong interview explanation should sound like this:

```text
FlowBridge Lite simulates the backend integration layer behind an enterprise banking system. Instead of building a full fake banking app, I focused on the workflow layer that receives an account-opening request, validates it, maps the client payload into a downstream core-banking format, persists workflow state, publishes a Kafka event, processes the event asynchronously, calls a mock downstream system, updates status, records audit logs, and supports retry for failed workflows.

I built it this way because real banks often have many internal systems with different data formats and business rules. The goal was to practice realistic backend concepts like layered architecture, DTO/entity separation, PostgreSQL persistence, payload transformation, Kafka async processing, workflow status transitions, auditability, idempotency, and integration testing.
```

---

## 32. Key Design Principles

Keep these principles throughout the project:

1. Build one workflow deeply instead of many workflows shallowly.
2. Keep controllers thin and services meaningful.
3. Use DTOs for API contracts and entities for persistence.
4. Use Flyway migrations instead of relying on automatic schema generation.
5. Make workflow status transitions explicit.
6. Include correlation IDs in logs, audit logs, and events.
7. Think about duplicate events and idempotency.
8. Write tests for business logic before adding more features.
9. Keep Docker Compose setup easy to run.
10. Make the README clear enough that a recruiter, interviewer, or another developer can understand the system quickly.

---

## 33. Codex Instruction

Use this document as the product and architecture specification for FlowBridge Lite.

When generating implementation steps, break work into small commits/milestones.

Prioritize this order:

1. Spring Boot project setup
2. PostgreSQL + Flyway
3. Entities/repositories
4. Account-opening API
5. Mapping service
6. Audit logging
7. Status transition validation
8. Correlation IDs and structured logs
9. Kafka producer
10. Kafka consumer
11. Mock core banking service
12. Retry logic
13. Idempotency
14. Unit tests
15. Testcontainers integration tests
16. Docker Compose
17. Swagger/OpenAPI
18. README polish

Do not generate Kubernetes, AWS, Redis, or Spring Security work until the MVP is fully working.
