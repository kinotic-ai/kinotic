# kinotic-test — AI Reference

> For narrative documentation see [README.md](./README.md).

## Build & Test Commands

```bash
./gradlew :kinotic-test:build      # compile
./gradlew :kinotic-test:test       # run unit tests
./gradlew :kinotic-test:check      # build + test + lint
```

## Hard Rules

Directives for AI working on this module (sourced from architectural constraints in the Notes section and code):

- Never add per-test container startup logic; always extend one of the three base classes (`KinoticTestBase`, `ElasticTestBase`, `KeycloakTestBase`) so that the static-initializer + volatile-flag sharing pattern is respected and containers are started only once per JVM.
- Never write tests that assume exclusive ownership of an Elasticsearch index name; always append a caller-supplied suffix (as `TestHelper` does) to avoid collisions when container state is shared across all test classes in a run.
- Do not resolve compose files from arbitrary paths; `KinoticTestConfiguration` expects compose files at `../deployment/docker-compose/` relative to the Gradle working directory, and full-stack tests must be run from within the mono-repo layout.
- Never bypass `@DirtiesContext(classMode = AFTER_CLASS)` on base classes; this annotation is required to prevent Spring bean state from leaking between test classes while still allowing containers to remain running across the full test suite.
- Always account for the Keycloak dual-port layout (port 8888 for application/token traffic, port 9000 for management/health); do not conflate these ports when configuring wait strategies or constructing token URLs.
- Do not use `DummySecurityService` in tests that require real OIDC validation; activate the `keycloak` Spring profile and set `oidc-security-service.enabled=true` to switch to `OidcSecurityService`, as `DummySecurityService` only accepts literal `guest/guest` credentials and will reject all others.

## Package Structure

```
org.kinotic.test
├── KinoticTestApplication          # @SpringBootApplication entry point, @EnableKinotic, active profile "test"
├── config
│   └── DummySecurityService        # No-op IAM for non-OIDC tests
├── support
│   ├── ContainerHealthChecker      # HTTP health polling utility
│   ├── elastic
│   │   ├── ElasticTestBase                       # Abstract base — Testcontainers Elasticsearch
│   │   ├── ElasticsearchTestConfiguration        # Static container + readiness lifecycle
│   │   └── ElasticsearchTestContextInitializer   # ApplicationContextInitializer bridge
│   ├── keycloak
│   │   ├── KeycloakTestBase                      # Abstract base — Testcontainers Keycloak
│   │   ├── KeyloakTestConfiguration              # Static container + readiness lifecycle
│   │   └── KeycloakTestContextInitializer        # ApplicationContextInitializer bridge (publishes keycloak.test.url)
│   └── kinotic
│       ├── KinoticTestBase                       # Abstract base — Docker Compose full stack
│       ├── KinoticTestConfiguration              # Static compose + readiness + migration polling lifecycle
│       └── KinoticTestContextInitializer         # ApplicationContextInitializer bridge
└── tests
    ├── auth                        # OIDC/JWT/access-control tests (KeycloakTestBase)
    ├── core
    │   ├── application             # ApplicationService CRUD (KinoticTestBase)
    │   ├── entity                  # EntityCrudTests, BulkUpdateTests (KinoticTestBase)
    │   ├── security.graphos        # PolicyEvaluator / PolicyAuthorizationService unit tests
    │   └── support                 # TestHelper, StructureAndPersonHolder
    ├── frontend                    # FrontendConfiguration integration (KinoticTestBase)
    └── sql                         # MigrationExecutor (ElasticTestBase), QueryBuilderTest, NotIndexedTypeTest
```

## Operation Flow

1. A test class annotated with `@SpringBootTest` extends one of the three base classes.
2. Spring Test discovers the `@ContextConfiguration(initializers = ...)` annotation inherited from the base class.
3. The initializer calls `*TestConfiguration.startContainersSynchronously()` if containers are not yet running, blocking until the `ContainerHealthChecker` confirms readiness.
4. The initializer injects `kinotic.persistence.elastic-connections[0].host/port/scheme` (and `keycloak.test.url` for Keycloak tests) into the `ConfigurableApplicationContext` via `TestPropertyValues`.
5. Spring Boot completes context creation with the injected properties pointing at the live containers.
6. Tests run; `@DirtiesContext(classMode = AFTER_CLASS)` on each base class tears down the application context after the last test in the class, while the containers themselves persist until JVM shutdown.

## Public API

| Class / Interface | Package | Role |
|---|---|---|
| `KinoticTestBase` | `support.kinotic` | Extend for full-stack (Elasticsearch + migration) tests |
| `ElasticTestBase` | `support.elastic` | Extend for Elasticsearch-only tests |
| `KeycloakTestBase` | `support.keycloak` | Extend for OIDC authentication tests |
| `ContainerHealthChecker` | `support` | Utility for HTTP health polling; also usable in custom initializers |
| `KinoticTestConfiguration` | `support.kinotic` | Static accessors: `getElasticsearchHost()`, `getElasticsearchPort()`, `areContainersReady()` |
| `ElasticsearchTestConfiguration` | `support.elastic` | Static accessors: `ELASTICSEARCH_CONTAINER`, `areContainersReady()`, `getContainerStatus()` |
| `KeyloakTestConfiguration` | `support.keycloak` | Static accessors: `KEYCLOAK_CONTAINER`, `getKeycloakUrl()`, `getKeycloakAuthUrl()` |
| `DummySecurityService` | `config` | Auto-registered `SecurityService` stub; disabled by setting `oidc-security-service.enabled=true` |
| `TestHelper` | `tests.core.support` | Spring `@Component` providing `createAndVerify(...)`, `bulkSaveCarsAsRawJson(...)`, `saveCarAsRawJson(...)`, and related factory methods |

## Module Dependencies

| Module | Reason |
|---|---|
| `kinotic-core` | `SecurityService` API, `OidcSecurityService`, `JwksService`, `Participant`, `OidcSecurityServiceProperties`, `@EnableKinotic` |
| `kinotic-domain` | `Application`, `ApplicationService`, domain model types |
| `kinotic-idl` | IDL type system used by the persistence layer under test |
| `kinotic-persistence` | `EntitiesService`, `EntityDefinition`, `EntityContext`, policy authorization (`PolicyEvaluator`, `PolicyAuthorizer`) |
| `kinotic-sql` | `MigrationExecutor`, `MigrationParser`, `Migration` — SQL-over-Elasticsearch migration tests |

External test dependencies: `testcontainers`, `testcontainers-elasticsearch`, `testcontainers-keycloak` (dasniko 3.8.0), `reactor-test`, `spring-security-oauth2-client`, `jjwt-api/impl/jackson`.
