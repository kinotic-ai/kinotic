<!--
================================================================================
CONSTITUTION SYNC IMPACT REPORT
================================================================================

Version Change: N/A → 1.0.0 (Initial Constitution)
Date: 2025-11-26
Governance Level: Guidance (aspirational, flexible interpretation)

--------------------------------------------------------------------------------
PRINCIPLES DEFINED (14 Core Principles)
--------------------------------------------------------------------------------

ARCHITECTURAL PRINCIPLES:
  I.   Library-First Architecture
       - All features start as reusable libraries consumed by applications
       - Separation between library implementation and application integration
  
  II.  Multi-Module Build Conventions
       - Gradle workspace with module-qualified command syntax
       - Consistent build patterns for reproducibility
  
  III. Convention over Configuration with MVP Focus
       - Sensible conventions that work immediately (RAD philosophy)
       - Configuration available for overrides, never required to start
       - MVP first, extend/refactor as concrete use cases emerge
       - Proper software patterns for modular, loosely coupled code
  
  IV.  Reactive Programming
       - Non-blocking I/O with Vert.x for web servers, OpenAPI, and GraphQL endpoints
       - Async patterns for Elasticsearch and external systems

DEVELOPMENT PRACTICES:
  V.   Stop and Ask When Uncertain
       - Explicit uncertainty handling over trial-and-error
       - Priority #1 constraint from cursorrules
  
  VI.  Integration-First Test-Driven Development
       - Design APIs/use cases → Developer approval → Implement → Integration/E2E tests
       - NO unit tests, NO mocks (exceptions require documented reasoning)
       - Integration tests with real dependencies via TestContainers
       - E2E tests for critical workflows
  
  VII. Documentation Last
       - Update docs after code changes and all tests passing
       - Keep READMEs, architecture diagrams, and API docs in sync
       - Doing this last ensures API details have settled 

  VIII. API Stability & Backward Compatibility
        - Public APIs maintain compatibility across versions
        - Semantic versioning with migration paths for breaking changes

SECURITY & MULTI-TENANCY:
  IX.  Security-First Approach
       - OIDC authentication as primary mechanism
       - JWT validation for signature, issuer, audience, expiration
       - Never skip security checks or make assumptions
  
  X.   Multi-Tenant by Design
       - Tenant isolation built into all data operations
       - Tenant ID from JWT claims, enforced at data layer

QUALITY & PERFORMANCE:
  XI.  Observability & Monitoring
       - OpenTelemetry integration, structured logging
       - Health check endpoints for all services
  
  XII. Performance Optimization
       - JWKS caching, GraphQL caching, connection pooling
       - k6 load testing, resource monitoring

FRAMEWORK DIFFERENTIATORS:
  XIII. Schema Evolution Support
        - Add fields without data migration
        - SQL dialect for Elasticsearch via structures-sql
        - Migration system for structural changes
  
  XIV.  TypeScript-First Client APIs
        - Entity definitions with TypeScript decorators
        - Auto-generated type-safe service classes
        - GraphQL and OpenAPI specifications generated from TypeScript
        - CLI code generation and synchronization

--------------------------------------------------------------------------------
SECTIONS ADDED
--------------------------------------------------------------------------------

✅ Technology Stack Requirements
   - Backend: Java 21+, Spring Boot 3.x, Vert.x (GraphQL/OpenAPI/Health), 
     Elasticsearch 8.x, GraphQL Java, JJWT 0.12.x, ANTLR4, 
     Continuum (Application Framework), Caffeine
   - Frontend: Vue.js 3, TypeScript, Vite, Pinia, oidc-client-ts, shadcn/ui,
     Tailwind CSS
   - Build & Deployment: Gradle 8.x, Docker Compose, Helm Charts, TestContainers
   - Authentication: Keycloak, Okta, Microsoft Entra ID, Google, GitHub, Custom OIDC
   - Monitoring: OpenTelemetry, structured logging, Elasticsearch/Kibana, Prometheus/Grafana

✅ Development Workflow
   - Code review requirements (tests, conventions, documentation)
   - Testing gates (integration, E2E, linter checks)
   - Deployment process (development with Docker Compose, production with Helm)

✅ Integration Patterns
   - GraphQL Federation (Apollo, schema composition, caching)
   - OIDC Authentication Flow (9-step flow from frontend to backend)
   - Elasticsearch Integration (connection pooling, tenant isolation, SQL dialect)
   - Frontend OIDC Integration (oidc-client-ts, token management, Pinia state)
   - CLI Code Generation (structures sync, auto-generated services)

✅ Module Responsibilities
   - Library Modules: structures-core, structures-sql, structures-auth
   - Application Modules: structures-server
   - Frontend Modules: structures-frontend-next
   - JavaScript Modules: structures-js (structures-api, structures-cli, load-generator, structures-e2e)

✅ Governance
   - Guidance-level governance (aspirational, flexible interpretation)
   - Amendment process with semantic versioning
   - Compliance expectations (recommended, not strictly enforced)
   - Version tracking and transparency

--------------------------------------------------------------------------------
TEMPLATE SYNC STATUS
--------------------------------------------------------------------------------

✅ .specify/templates/plan-template.md
   - Constitution Check section can reference these 14 principles
   - Technical Context aligns with Technology Stack Requirements
   - Complexity Tracking aligns with governance justification requirements
   - No updates required - template already compatible

✅ .specify/templates/spec-template.md
   - User scenarios align with TDD principle (VI)
   - Requirements align with Documentation First principle (VII)
   - Success criteria supported by all principles
   - No updates required - template already compatible

✅ .specify/templates/tasks-template.md
   - Test-first approach enforced by TDD principle (VI)
   - User story organization supported by Development Workflow
   - Phase dependencies align with systematic development principles
   - No updates required - template already compatible

✅ .specify/templates/checklist-template.md
   - Not reviewed (checklist template is generic)
   - No updates anticipated

✅ .specify/templates/agent-file-template.md
   - Not reviewed (agent template is generic)
   - No updates anticipated

--------------------------------------------------------------------------------
FEATURES COVERED IN CONSTITUTION
--------------------------------------------------------------------------------

✅ GraphQL federation patterns (Principle IV, Integration Patterns)
✅ OIDC multi-provider authentication (Principle IX, Integration Patterns, Tech Stack)
✅ Elasticsearch integration patterns (Principle XIII, Integration Patterns)
✅ Frontend OIDC flows and state management (Integration Patterns, Module Responsibilities)
✅ CLI tools and code generation (Principle XIV, Integration Patterns)
✅ Testing strategies (unit, integration, E2E) (Principle VI, Development Workflow)
✅ Docker Compose and Kubernetes deployment (Tech Stack, Development Workflow)
✅ Migration system principles (Principle XIII, Integration Patterns)
✅ Schema evolution patterns (Principle XIII - dedicated principle)
✅ Real-time synchronization with Continuum (Tech Stack, CLI patterns)
✅ Load testing and performance standards (Principle XII)
✅ Licensing (Elastic License 2.0 referenced in README)

--------------------------------------------------------------------------------
DEFERRED ITEMS / FOLLOW-UP TODOS
--------------------------------------------------------------------------------

None - Constitution is complete and ready for use.

All placeholders filled:
  ✅ [PROJECT_NAME] → Structures Framework
  ✅ [CONSTITUTION_VERSION] → 1.0.0
  ✅ [RATIFICATION_DATE] → 2025-11-26
  ✅ [LAST_AMENDED_DATE] → 2025-11-26
  ✅ All 14+ principles defined with detailed requirements and rationales
  ✅ All additional sections populated with comprehensive content
  ✅ Governance model established (guidance-level)

--------------------------------------------------------------------------------
SUGGESTED COMMIT MESSAGE
--------------------------------------------------------------------------------

docs: create Structures Framework Constitution v1.0.0

Initial constitution establishing 14 core principles and governance framework
for the Structures project. Constitution provides aspirational guidance for:

- Architectural principles (library-first, convention over config, reactive)
- Development practices (stop-and-ask, integration-first TDD, docs-last)
- Security & multi-tenancy (security-first, tenant isolation)
- Quality & performance (observability, optimization)
- Framework differentiators (schema evolution, TypeScript-first)

Key Philosophy:
- Convention over Configuration with MVP focus (RAD approach)
- Integration-first testing (NO unit tests, NO mocks)
- Documentation last (after tests pass)
- Modular, loosely coupled code for localized changes

Includes comprehensive sections on technology stack, development workflow,
integration patterns, and module responsibilities.

Governance: Guidance-level (flexible interpretation, no strict enforcement)

================================================================================
-->

# Structures Framework Constitution

## Core Principles

### I. Library-First Architecture

Every feature MUST start as a reusable library before being consumed by applications. Libraries provide core functionality through well-defined APIs that applications consume via Spring Boot auto-configuration.

**Requirements:**
- All domain logic resides in library modules (`structures-core`, `structures-sql`, `structures-auth`)
- Libraries MUST be self-contained and independently testable
- Libraries expose functionality through Spring Boot `@ConfigurationProperties` and service interfaces
- Application modules (`structures-server`) ONLY consume library functionality via `@EnableStructures` and dependency injection
- Clear separation: libraries implement, applications integrate

**Rationale:** This ensures reusability, testability, and prevents tight coupling between business logic and application infrastructure. Multiple applications can consume the same library functionality with different configurations.

### II. Multi-Module Build Conventions

This is a multi-module Gradle workspace. All build commands MUST be executed from the project root using module-qualified syntax.

**Requirements:**
- Always run: `./gradlew :module-name:task` from project root
- Never run: `cd module-name && ./gradlew task`
- Build conventions are defined in `buildSrc/` and shared across modules
- Dependencies are managed through Gradle's dependency management
- Module structure: `structures-*` naming convention for all modules

**Rationale:** Consistent build patterns prevent errors, enable reliable CI/CD pipelines, and ensure reproducible builds across all environments. The Gradle wrapper ensures consistent build tool versions.

### III. Convention over Configuration with MVP Focus

Structures and Continuum prioritize sensible conventions that work immediately, enabling Rapid Application Development (RAD). Configuration is available for environment-specific overrides but NEVER required to start. Development focuses on MVP delivery first, then extends with proper software patterns as concrete use cases emerge.

**Requirements:**
- Start with working conventions that require zero configuration
- Provide sensible defaults that deliver immediate value out-of-the-box
- Configuration available through Spring Boot `@ConfigurationProperties` for overrides only
- Support environment-specific profiles (dev, test, production) when customization needed
- Focus on MVP implementation first, extend or refactor as real use cases arise
- Apply proper software patterns (SOLID, DRY, modularity) to create loosely coupled code
- Changes should be localized without major system-wide impacts
- Hard-code sensible defaults, make them overridable where flexibility is needed

**Rationale:** Convention over configuration accelerates development by eliminating unnecessary decisions. MVP focus delivers value quickly and validates assumptions before over-engineering. Modular design with proper patterns enables incremental evolution as requirements become concrete, avoiding premature optimization and speculative features. This RAD approach is core to both Continuum and Structures philosophy.

### IV. Reactive Programming

Non-blocking I/O MUST be used for all network operations and external system interactions to maximize throughput and resource utilization.

**Requirements:**
- Vert.x is the reactive foundation for web servers, OpenAPI, and GraphQL endpoints
- Elasticsearch operations use async clients with reactive patterns
- Spring WebFlux patterns for reactive endpoints where applicable
- Avoid blocking operations in the event loop
- Use appropriate thread pools for blocking operations when unavoidable

**Rationale:** Reactive programming enables high concurrency with minimal resource overhead, essential for data platform performance at scale. Non-blocking I/O prevents thread exhaustion under load.

### V. Stop and Ask When Uncertain

When the path forward is unclear, STOP and ask for guidance rather than experimenting with multiple approaches.

**Requirements:**
- If unsure how to solve a problem, explicitly ask the user or team for direction
- Document the uncertainty and considered alternatives before asking
- Avoid blindly trying different implementations hoping one works
- This applies to architectural decisions, security configurations, and complex integrations
- Prefer clarification over assumption

**Rationale:** This principle prevents wasted time, avoids introducing bugs or security vulnerabilities through trial-and-error, and ensures decisions are made with appropriate context and expertise.

### VI. Integration-First Test-Driven Development

APIs and use cases MUST be designed and approved before implementation. Tests use real dependencies without mocks to validate actual system behavior.

**Requirements:**
- Design APIs and use cases → Get developer approval → Implement → Write integration/E2E tests → Watch tests pass
- NO unit tests - focus exclusively on integration and E2E tests
- NO mocks - test against real dependencies (Elasticsearch, databases, services)
- Use TestContainers for isolated, reproducible integration testing with real services
- E2E tests for critical user workflows
- Exceptions to the "no mocks" rule require documented reasoning explaining why real dependencies cannot be used
- Test coverage validates real system behavior, not isolated units
- Integration tests prove the system works as a whole

**Rationale:** Unit tests with mocks validate code in isolation but miss integration issues, configuration problems, and real-world behavior. Integration tests with real dependencies catch actual bugs and prove the system works. Mocks create false confidence and maintenance overhead. By testing with real services via TestContainers, we validate true behavior while maintaining test isolation and reproducibility. This approach aligns with our focus on working software over theoretical correctness.

### VII. Documentation Last

Documentation MUST be updated after code changes and all tests are passing. This ensures API details have settled before documenting them.

**Requirements:**
- Update module READMEs after implementing and testing features
- Add inline documentation (Javadoc, JSDoc) after implementation details are finalized
- Keep architecture diagrams in sync with validated implementation
- OIDC and security configurations documented comprehensively after testing
- API changes reflected in documentation after tests confirm the final API shape

**Rationale:** Documentation-last ensures we document the actual implemented behavior rather than planned behavior. APIs often evolve during implementation and testing. Documenting after tests pass guarantees accuracy and prevents stale documentation of abandoned approaches. This aligns with TDD where implementation follows tests, and documentation follows confirmed implementation.

### VIII. API Stability & Backward Compatibility

Public APIs in library modules MUST maintain backward compatibility. Breaking changes require major version bumps and migration paths.

**Requirements:**
- Public APIs (package `api/`) must remain stable
- Internal APIs (package `internal/`) can change freely
- Deprecate before removing public features
- Provide migration guides for breaking changes
- Use semantic versioning: MAJOR.MINOR.PATCH
- Schema changes must support evolution without data migration where possible

**Rationale:** API stability enables users to upgrade confidently, reduces integration friction, and maintains ecosystem trust. Internal flexibility allows continuous improvement without external disruption.

### IX. Security-First Approach

Security considerations MUST be primary drivers of design decisions. Never skip security checks or make security-related assumptions without explicit confirmation.

**Requirements:**
- OIDC authentication is the primary authentication mechanism
- JWT tokens MUST be validated for signature, issuer, audience, and expiration
- Never skip token validation or security checks
- Multi-provider authentication configurations are never assumed safe without explicit validation
- HTTPS required in production environments
- Sensitive information never logged or exposed in error messages
- Cross-provider configurations require explicit security review

**Rationale:** Security vulnerabilities can have catastrophic consequences. Explicit verification prevents assumptions that lead to security holes. OIDC provides industry-standard, proven authentication patterns.

### X. Multi-Tenant by Design

Tenant isolation MUST be built into all data operations and security contexts from the ground up.

**Requirements:**
- Tenant ID automatically included in all Elasticsearch operations
- Tenant context extracted from JWT claims
- GraphQL and REST APIs tenant-aware by default
- No cross-tenant data leakage possible through any API
- Tenant isolation tested in integration tests
- Index strategies support tenant-specific configurations

**Rationale:** Multi-tenancy is a core framework feature, not an afterthought. Built-in isolation prevents security issues and enables SaaS deployment patterns. Retrofitting tenant isolation is error-prone and expensive.

### XI. Observability & Monitoring

Applications MUST be observable through structured logging, metrics, and distributed tracing.

**Requirements:**
- OpenTelemetry integration for distributed tracing
- Structured JSON logging for all components
- Health check endpoints (`/health/`) for all services
- Metrics exposed for monitoring systems
- Log levels configurable at runtime
- Error logging includes context without sensitive data

**Rationale:** Observability enables rapid debugging, performance optimization, and operational excellence. Structured data enables automated analysis and alerting in production environments.

### XII. Performance Optimization

Performance MUST be considered throughout design and implementation using proven optimization patterns.

**Requirements:**
- JWKS caching for efficient public key validation
- GraphQL schema and operation definition caching
- Elasticsearch connection pooling and optimization
- Caffeine cache for frequently accessed data
- Reactive patterns prevent blocking under load
- Performance testing with k6 for load scenarios
- Resource usage monitored and bounded

**Rationale:** Performance determines user experience and operational costs. Systematic optimization prevents performance problems rather than reacting to them. Caching and pooling are essential at scale.

### XIII. Schema Evolution Support

Data schemas MUST evolve over time without breaking existing data or requiring complex migrations.

**Requirements:**
- New fields can be added to entities without data migration
- Field types support safe evolution patterns
- Version information tracked at the schema level
- Migration system available for necessary structural changes
- SQL dialect for Elasticsearch operations via structures-sql
- Backward-compatible schema changes preferred over migrations

**Rationale:** Schema flexibility is a key differentiator of Structures. Traditional databases force painful migrations. Supporting additive changes without migrations dramatically improves development velocity and reduces operational risk.

### XIV. TypeScript-First Client APIs

Client-facing APIs MUST prioritize TypeScript definitions, code generation, and type safety.

**Requirements:**
- Entities defined in TypeScript with decorators (`@Entity`, `@AutoGeneratedId`)
- CLI tools automatically generate type-safe service classes
- GraphQL schema generated from TypeScript definitions
- OpenAPI specifications generated from TypeScript definitions
- API clients provide full type safety
- structures-api package provides core TypeScript types and decorators
- Documentation includes TypeScript examples

**Rationale:** TypeScript-first development enables IDE intelligence, catches errors at compile time, and provides excellent developer experience. Auto-generation reduces boilerplate and keeps clients in sync with schemas.

## Technology Stack Requirements

The Structures framework is built on a carefully selected technology stack that enables its core capabilities.

### Backend Technologies

**Required:**
- **Java 21+**: Modern Java features and performance improvements
- **Spring Boot 3.x**: Auto-configuration, dependency injection, and ecosystem integration
- **Vert.x**: Reactive web server and GraphQL/OpenAPI/Health endpoint support
- **Elasticsearch 8.x**: Search engine and primary data store
- **GraphQL Java**: GraphQL implementation with Apollo Federation support
- **JJWT 0.12.x**: JWT token handling and validation
- **ANTLR4**: SQL parsing for structures-sql dialect
- **Continuum Framework**: Core integration for Application Framework capabilities

**Caching & Performance:**
- **Caffeine**: High-performance in-memory caching

### Frontend Technologies

**Required:**
- **Vue.js 3**: Modern reactive framework with Composition API
- **TypeScript**: Type-safe JavaScript development
- **Vite**: Fast build tool and development server
- **Pinia**: State management for Vue applications
- **oidc-client-ts**: OIDC authentication library for frontend

**UI Components:**
- **shadcn/ui**: Modern, accessible UI component library
- **Tailwind CSS**: Utility-first CSS framework

### Build & Deployment

**Required:**
- **Gradle 8.x**: Multi-module build system with custom conventions
- **pnpm/npm**: JavaScript package management
- **Docker Compose**: Local development and testing
- **Helm Charts**: Kubernetes deployment templates
- **TestContainers**: Isolated testing with containerized dependencies

### Authentication & Security

**Supported Providers:**
- Keycloak (open-source identity management)
- Okta (enterprise SSO)
- Microsoft Entra ID (Azure Active Directory)
- Google Workspace
- GitHub OAuth
- Custom OIDC-compliant providers

### Monitoring & Observability

**Required:**
- **OpenTelemetry**: Distributed tracing and metrics
- **Structured JSON Logging**: Machine-readable log output
- Elasticsearch/Kibana for log aggregation (optional)
- Prometheus/Grafana for metrics (optional)

## Development Workflow

### Code Review Requirements

**All changes must:**
- Pass automated tests (unit, integration, E2E)
- Follow existing code conventions and patterns
- Include updated documentation
- Have meaningful commit messages
- Address linter warnings and errors

**Security-related changes require:**
- Explicit security review and discussion
- Test coverage for security boundaries
- Documentation of security implications

### Testing Gates

**Before merge:**
- All integration tests with TestContainers passing
- E2E tests for affected workflows passing
- No new linter errors introduced
- Documentation updated (after tests pass)
- Breaking changes documented with migration paths

**For library modules:**
- Public API changes reviewed for backward compatibility
- Deprecation warnings for removed features
- Integration test coverage for all public APIs (no unit tests, no mocks)

### Deployment Process

**Development:**
- Docker Compose environment with all services
- Hot reload enabled for rapid iteration
- Full OIDC authentication testing with Keycloak
- Schema generation tools available

**Production:**
- Kubernetes deployment via Helm charts
- Multi-instance deployment for high availability
- External Elasticsearch cluster
- Production OIDC providers configured
- Monitoring and alerting enabled
- Health checks for readiness and liveness

## Integration Patterns

### GraphQL Federation

**Pattern:**
- Apollo Federation for schema composition
- Each module can contribute to the federated graph
- Schema caching for performance
- Operation definitions cached and reused
- DataFetchers implement resolvers for domain objects

### OIDC Authentication Flow

**Pattern:**
1. Frontend redirects to OIDC provider for authentication
2. Provider redirects back with authorization code
3. Frontend exchanges code for JWT tokens
4. JWT sent to backend in Authorization header
5. Backend validates JWT signature via JWKS
6. Backend validates issuer, audience, and expiration
7. Backend extracts user info and roles from claims
8. Tenant ID extracted from JWT for multi-tenancy
9. Request processed with user and tenant context

### Elasticsearch Integration

**Pattern:**
- Elasticsearch client configured via structures-core
- Connection pooling for performance
- Index prefix (`struct_`) for namespace isolation
- Tenant ID field (`structuresTenantId`) for multi-tenancy
- SQL dialect via structures-sql for familiar query syntax
- Migration system for schema evolution
- Async operations with reactive patterns

### Frontend OIDC Integration

**Pattern:**
- oidc-client-ts library for OIDC flows
- Token storage in memory (not localStorage)
- Silent token renewal via hidden iframe
- Multi-provider selection based on email domain
- Pinia store for authentication state
- Automatic token inclusion in API requests

### CLI Code Generation

**Pattern:**
- TypeScript entity definitions with decorators
- `structures sync` command syncs with server
- Auto-generated service classes for entities
- Type-safe CRUD operations
- GraphQL queries generated from entity schemas
- Real-time subscriptions via Continuum (when configured)

## Module Responsibilities

### Library Modules

**structures-core:**
- OIDC authentication and JWT validation
- GraphQL API implementation and federation
- OpenAPI implementation 
- Elasticsearch client configuration and operations
- Domain services (Entity, Structure, Application, Project)
- Vert.x web server and endpoint handlers
- Migration system execution
- Multi-tenant data isolation

**structures-sql:**
- SQL parsing with ANTLR4
- Custom SQL dialect for Elasticsearch
- Type mapping between SQL and Elasticsearch
- Migration file parsing and execution
- Query building and optimization

**structures-auth:**
- JWT token management
- OIDC provider configuration
- Authorization services and role management
- Security service interfaces
- Spring Boot auto-configuration for security

### Application Modules

**structures-server:**
- Spring Boot application server
- Consumes structures-core via `@EnableStructures`
- API gateway for GraphQL and REST
- Static file serving for frontend
- Health check endpoints
- CORS configuration
- Production deployment artifact

### Frontend Modules

**structures-frontend-next:**
- Vue 3 application with Composition API
- OIDC authentication UI
- Multi-provider authentication selection
- Data management interfaces
- Schema browser and editor
- Responsive design with Tailwind CSS
- Pinia state management

### JavaScript Modules

**structures-js:**
- **structures-api**: TypeScript types and decorators for entity definitions
- **structures-cli**: Command-line tools for project initialization and synchronization
- **load-generator**: k6-based load testing tools
- **structures-e2e**: Playwright-based end-to-end tests

## Governance

This constitution provides **aspirational guidance and best practices** for the Structures framework. Principles guide decision-making and maintain architectural coherence while allowing flexible interpretation based on context.

### Amendment Process

- Constitution changes proposed through discussion
- Major principle changes increment MAJOR version
- New principles or sections increment MINOR version
- Clarifications and wording updates increment PATCH version
- Amendments documented in commit history
- No formal approval process required (guidance-level governance)

### Compliance

- Principles are strongly recommended but not strictly enforced
- Code reviews should reference relevant principles
- Deviations from principles should be documented with rationale
- Critical principles (security, multi-tenancy) have higher compliance expectations
- Complexity and technical debt should be justified

### Version Tracking

- Constitution version follows semantic versioning
- Version history tracked in git commits
- Breaking governance changes require major version bump
- Transparency in governance evolution

### Development Guidance

- Use this constitution as a reference during design and review
- Cite specific principles when discussing architectural decisions
- Update constitution when new patterns emerge
- Keep principles aligned with actual practice

**Version**: 1.0.0 | **Ratified**: 2025-11-26 | **Last Amended**: 2025-11-26
