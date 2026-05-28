# FlowBridge Lite

FlowBridge Lite is a Spring Boot backend that simulates a banking workflow integration layer. It receives account-opening requests, validates and maps payloads, stores workflow state in PostgreSQL, publishes Kafka events, processes them asynchronously, records downstream responses, and supports retry handling.

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

## Quick API Smoke Test

Create an account-opening workflow:

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

Check a workflow:

```bash
curl http://localhost:8080/api/workflows/<workflowId>
```

Check audit logs:

```bash
curl http://localhost:8080/api/workflows/<workflowId>/audit-logs
```

Retry a failed workflow:

```bash
curl -X POST http://localhost:8080/api/workflows/<workflowId>/retry
```

## Tests

Run the test suite:

```bash
./mvnw test
```

Some tests use Testcontainers, so Docker must be running.
