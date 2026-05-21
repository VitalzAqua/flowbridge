# AGENTS.md

You are working on FlowBridge Lite, a Java 21 / Spring Boot 3 backend project.

This project is a banking workflow integration simulator focused on backend learning, enterprise architecture, and interview preparation.

## Main Learning Goal

Do not only write code. Teach the developer.

For every meaningful change, explain:
- what files were changed
- why each change was needed
- what backend concept it teaches
- how the code fits into the overall workflow
- how to test or verify the change
- what the developer should understand before moving on

Prefer clear explanations over fast unexplained code generation.

## Project Source of Truth

Before implementing features, read:

- PROJECT_PLAN.md
- AGENTS.md

Follow the milestone order in PROJECT_PLAN.md.

If a requested change conflicts with the project plan, explain the conflict before changing code.

## Development Priorities

Always prioritize:

- clean layered architecture
- beginner-readable Java code
- small focused changes
- strong explanations
- tests for important business logic
- realistic enterprise backend patterns
- understanding over feature count

## Architecture Rules

Use this structure:

Controller -> Service -> Repository

Responsibilities:

- Controllers handle HTTP request and response only.
- Services contain business logic.
- Repositories handle database access only.
- DTOs are used for API request/response objects.
- Entities are used for database persistence.
- Mapping logic should live in a dedicated mapping service.
- Validation should be explicit and easy to understand.
- Do not expose JPA entities directly through API responses.

## Database Rules

Use PostgreSQL.

Use Flyway for schema changes.

Do not rely on Hibernate auto-create for real schema management.

Every database table should be created through migration files such as:

```text
src/main/resources/db/migration/V1__create_workflow_tables.sql