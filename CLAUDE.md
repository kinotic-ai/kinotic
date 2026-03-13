# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

Kinotic OS is a cloud operating system that removes infrastructure plumbing (IAM, VPCs, CI/CD, service meshes) and lets developers deploy enterprise-grade, internet-scale applications from rapid prototyping to Kubernetes in minutes. It is built on Spring Boot 4, Apache Ignite (clustering), Vert.x (non-blocking I/O), Project Reactor, and Elasticsearch as the primary data store.

## Build Commands

```bash
# Build a single module (no tests)
./gradlew :kinotic-core:build

# Run unit tests for a single module
./gradlew :kinotic-core:test

# Build + test + lint a single module
./gradlew :kinotic-core:check

# Build all modules (skip tests — matches CI)
./gradlew build -x test

# Run all tests across all modules
./gradlew test
```

Replace `:kinotic-core` with any module name (e.g., `:kinotic-persistence`, `:kinotic-rpc-gateway`).

## Module Map

Each module has its own `CLAUDE.md` with hard rules, package structure, and operation flow. Read the relevant one before modifying a module.

| Module | Responsibility |
|---|---|
| `kinotic-core` | Foundation: service registry, RPC proxy generation, event bus (Vert.x + Ignite), security, session management |
| `kinotic-domain` | Shared entity models (Application, Project), CRUD abstractions, Elasticsearch index management |
| `kinotic-idl` | Interface Definition Language: Java → C3Type schema introspection, IDL converter framework |
| `kinotic-persistence` | Schema-driven entity CRUD over Elasticsearch; auto-generates OpenAPI and GraphQL endpoints per EntityDefinition |
| `kinotic-rpc-gateway` | STOMP 1.2 over WebSocket gateway; thin transport adapter, no business logic |
| `kinotic-sql` | ANTLR4-based KinoticSQL grammar, migration execution, Elasticsearch DDL/DML translation |
| `kinotic-migration` | Standalone CLI app that applies `V<N>__<description>.sql` migrations on startup, then exits |
| `kinotic-orchestrator` | Fluent job DSL ("Grind"): sequential/parallel step execution via Reactor Flux |
| `kinotic-test` | Testcontainers integration test infrastructure (Elasticsearch, Keycloak, Docker Compose) |
| `kinotic-server` | Main Spring Boot application entry point (`@EnableKinotic`) |
| `kinotic-js/` | TypeScript projects: `structures-api` (RPC client lib), `structures-cli`, e2e tests, load generator |
| `structures-frontend-next/` | Next.js frontend |

## High-Level Architecture

### Service-Oriented RPC

- Services are registered: `ServiceRegistry.register(identifier, MyService.class, instance)`
- Consumers get JDK dynamic proxies: `ServiceRegistry.serviceProxy(identifier, MyService.class)`
- All RPC routes through Vert.x event bus (`EventBusService`) for local, or Ignite (`EventStreamService`) for clustered
- Return types: `Mono<T>`, `Flux<T>`, `CompletableFuture<T>`, or synchronous `T`
- Use `@Publish` on the **interface** (never the implementation) to auto-register a service

### Addressing (CRI)

Every resource has a CRI (Kinotic Resource Identifier): `scheme://[scope@]resourceName[/path][#version]`
- `srv://` — RPC service calls
- `stream://` — persistent ordered streams (requires Ignite; unavailable when `kinotic.disableClustering=true`)

### Schema-Driven Data Layer

1. Define an `EntityDefinition` (name, `ObjectC3Type` schema, decorators, multi-tenancy mode)
2. Publish it via `EntitiesService.publishEntityDefinition(...)`
3. On publication: Elasticsearch index is created, OpenAPI paths are generated, GraphQL schema is built
4. Decorators (e.g., `IdDecorator`, `PolicyDecorator`, `TenantIdDecorator`) run a pipeline before every write

### Security

- `SecurityService.authenticate(headers)` → `Participant` (id, tenantId, roles)
- `SessionManager.create(participant, replyToId)` → `Session` with CRI-pattern send/subscribe rules
- `TemporarySecurityService` (hardcoded credentials `kinotic`/`kinotic`) is dev-only — **never deploy to production**
- Fine-grained RBAC uses Cedar Policy Language at system/org/app levels

### Auto-Configuration Package Naming

All Spring Boot auto-configuration classes must live in a package ending with `_autoconfig` (underscore), **not** inside the module's main package tree. This prevents double-registration when Spring component scanning and the service-loader mechanism both run. Examples:
- `org.kinotic.core_autoconfig`
- `org.kinotic.idl_autoconfig`
- `org.kinotic.sql_autoconfig`

## Integration Tests

Integration tests extend one of three base classes from `kinotic-test`:

| Base Class | Containers |
|---|---|
| `KinoticTestBase` | Full stack: Elasticsearch + migration |
| `ElasticTestBase` | Elasticsearch only |
| `KeycloakTestBase` | OIDC authentication (Keycloak on ports 8888 app / 9000 management) |

Never add per-test container setup. Always use `@DirtiesContext(classMode = AFTER_CLASS)`.

## Key Cross-Cutting Rules

- **Jackson 3.x** (`tools.jackson.core`) is used in `kinotic-domain` and `kinotic-persistence` — do not use `com.fasterxml.jackson`.
- **Compile with `-parameters`** — required for named parameter capture in IDL schema introspection.
- **Never mutate migration versions** after they have been applied to any environment.
- **ANTLR4 grammar** lives in `kinotic-sql` as `KinoticSQL.g4` — never edit the generated parser/lexer files directly; edit the grammar and regenerate.

## CI/CD

GitHub Actions (`.github/workflows/gradle-build.yml`):
1. Builds with `./gradlew build -x test` (Java 25, Zulu)
2. Publishes JARs to Maven Central via JReleaser
3. Builds and pushes Docker images via `./gradlew bootBuildImage --publishImage`

Triggers on pushes to `develop`, `main`, `monoRepoRefactoring`, `2.x`.