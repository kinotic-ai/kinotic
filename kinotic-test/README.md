# kinotic-test

Integration test harness for the Kinotic platform, providing shared infrastructure, base test classes, and test coverage for all other modules.

## Overview

Testing distributed systems that depend on Elasticsearch, OIDC authentication, and schema migration requires reproducible, isolated infrastructure that starts and stops reliably alongside the Spring application context. Without a shared harness, each module would duplicate container management logic and Spring Boot test configuration, making tests brittle and expensive to maintain.

`kinotic-test` addresses this by providing three distinct infrastructure tiers — a full Docker Compose stack (Elasticsearch + schema migration), a lightweight Testcontainers Elasticsearch instance, and a Testcontainers Keycloak instance — each exposed through a dedicated abstract base class. Tests extend the appropriate base class and inherit the correct container lifecycle, Spring context configuration, and property injection automatically.

The module is the sole consumer of every other Kinotic module (`kinotic-core`, `kinotic-domain`, `kinotic-idl`, `kinotic-persistence`, `kinotic-sql`), meaning it serves as the integration boundary where cross-module behaviour is verified end-to-end. Concerns covered include entity CRUD and multi-tenant search via `EntitiesService`, schema migration via `MigrationExecutor`, OIDC token validation and role-based access control via `OidcSecurityService`, and policy-based field authorization via the `PolicyEvaluator` family.

Container readiness is coordinated through a `ContainerHealthChecker` utility that polls HTTP health endpoints (`/_cluster/health` for Elasticsearch, `/health/ready` for Keycloak) with configurable retry budgets, and all infrastructure classes use a static-initializer + volatile-flag pattern with `synchronized` monitor objects so that parallel test classes share a single container set per JVM.

## Key Concepts

- **`KinoticTestBase`** — Abstract base class for tests requiring the full stack. Registers `KinoticTestContextInitializer` as a `ContextConfiguration` initializer, starts `compose.kinotic-test.yml` (Elasticsearch + `kinotic-migration`), waits for the migration container to exit with code 0, then injects live Elasticsearch connection properties into the Spring environment before the application context is created.

- **`ElasticTestBase`** — Abstract base class for tests that need only an Elasticsearch node. Uses Testcontainers (`ElasticsearchContainer`) managed by `ElasticsearchTestConfiguration`, with security disabled and a `/_cluster/health` wait strategy. ARM/M-series macOS hosts automatically receive the `-XX:UseSVE=0` JVM flag to work around an upstream JDK bug.

- **`KeycloakTestBase`** — Abstract base class for tests that exercise OIDC authentication. Uses Testcontainers (`KeycloakContainer` 26.0.2) managed by `KeyloakTestConfiguration`, listening on port 8888 (app) and 9000 (management/health). When a `keycloak-realm-export.json` classpath resource is present it is bind-mounted into `/opt/keycloak/data/import/` and imported at startup. The resolved auth URL is published to the `keycloak.test.url` system property so YAML profiles can reference it via `${keycloak.test.url}`.

- **`KinoticTestConfiguration`** — Static infrastructure class for the Docker Compose stack. Resolves compose files from `../deployment/docker-compose/` relative to the working directory, applies an Apple Silicon override file when the JVM reports `os.arch=aarch64`, polls the Docker daemon directly (via `DockerClientFactory`) for migration container exit status with a 10-minute budget, and registers a JVM shutdown hook to stop the compose project.

- **`ElasticsearchTestConfiguration`** — Static infrastructure class for the Testcontainers Elasticsearch node. Manages a single `ElasticsearchContainer` as a class-level static field shared across all tests that extend `ElasticTestBase` in the same JVM, coordinating readiness through a `volatile boolean` flag and a `synchronized` monitor.

- **`KeyloakTestConfiguration`** — Static infrastructure class for the Testcontainers Keycloak node. Exposes `getKeycloakUrl()` and `getKeycloakAuthUrl()` for tests that need to acquire tokens programmatically from the `/realms/test/protocol/openid-connect/token` endpoint.

- **`ContainerHealthChecker`** — Stateless utility that checks health endpoints over plain HTTP (Java 11 `HttpClient`) and implements a polling loop with configurable `maxAttempts` and `delayMs`. Supports Elasticsearch (`/_cluster/health`, green or yellow), Keycloak (`/health/ready`), and the Kinotic server (`/health`).

- **`DummySecurityService`** — A `SecurityService` implementation that accepts `guest/guest` credentials (both STOMP-style `login`/`passcode` headers and HTTP Basic `authorization` headers) and returns an `ADMIN`-role participant. Active by default (`@ConditionalOnProperty` with `oidc-security-service.enabled=false`), bypassed when the `keycloak` profile is active and `oidc-security-service.enabled=true`.

## Configuration

### Spring profiles

| Profile | Effect |
|---|---|
| `test` (default, set at JVM level and in `application.yml`) | Activates `DummySecurityService` |
| `keycloak` | Activates `oidc-security-service.enabled=true`; used by all tests in `tests.auth` |
| `keycloak-admin-provider` | Configures a single `keycloak-admin` OIDC provider for admin access control tests |
| `keycloak-unauthorized-provider` | Configures an `unauthorized` provider for negative access control tests |

### Key properties

| Property | Default / Description |
|---|---|
| `kinotic.persistence.elastic-connections[0].host` | Injected at runtime from container host |
| `kinotic.persistence.elastic-connections[0].port` | Injected at runtime from container mapped port |
| `kinotic.persistence.elastic-connections[0].scheme` | `http` |
| `kinotic.persistence.web-server-port` | `8989` |
| `kinotic.persistence.cors-allowed-origin-pattern` | `*` |
| `kinotic.debug` | `true` |
| `kinotic.maxNumberOfCoresToUse` | `4` |
| `oidc-security-service.enabled` | `false` by default; `true` under `keycloak` profile |
| `oidc-security-service.oidc-providers[*].authority` | `${keycloak.test.url:http://localhost:8888/realms/test}` — resolved to container URL at test startup |
| `keycloak.test.url` | System property set by `KeycloakTestContextInitializer` |
| `spring.main.allow-bean-definition-overriding` | `true` (required to replace auto-configured beans in tests) |

### Auto-configuration

`KinoticTestApplication` carries `@EnableKinotic` which activates the platform's auto-configuration. `@EnableConfigurationProperties` ensures all `*Properties` beans are bound from the injected properties before any bean that depends on them is created.

## Usage Example

The following shows how to write an entity persistence test that relies on the full Docker Compose stack:

```java
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.internal.api.domain.DefaultEntityContext;
import org.kinotic.persistence.internal.sample.DummyParticipant;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.tests.core.support.StructureAndPersonHolder;
import org.kinotic.test.tests.core.support.TestHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MyEntityTest extends KinoticTestBase {

    @Autowired
    private TestHelper testHelper;

    @Autowired
    private EntitiesService entitiesService;

    @Test
    public void testCountByTenant() {
        var ctx = new DefaultEntityContext(new DummyParticipant("acme", "alice"));
        StructureAndPersonHolder holder = testHelper.createAndVerify(5, true, ctx, "_myTest");
        entitiesService.syncIndex(holder.getEntityDefinition().getId(), ctx).join();
        long count = entitiesService.count(holder.getEntityDefinition().getId(), ctx).join();
        Assertions.assertEquals(5L, count);
    }
}
```

For Keycloak-based OIDC tests, extend `KeycloakTestBase` and activate the `keycloak` Spring profile instead:

```java
@SpringBootTest
@ActiveProfiles("keycloak")
public class MyOidcTest extends KeycloakTestBase {

    @Autowired
    private OidcSecurityService securityService;

    @Test
    public void testValidToken() throws Exception {
        // Acquire a real token from the containerised Keycloak instance
        String tokenUrl = KeyloakTestConfiguration.getKeycloakUrl()
                + "/realms/test/protocol/openid-connect/token";
        // ... fetch token via HTTP POST, then authenticate:
        Participant p = securityService
                .authenticate(Map.of("authorization", "Bearer " + token)).join();
        assertTrue(p.getRoles().contains("user"));
    }
}
```

## Notes

- **Static container sharing.** All three `*TestConfiguration` classes use class-level `static` fields for containers and a `volatile boolean containersReady` flag. This means a container is started once per JVM process regardless of how many test classes extend the same base, which significantly reduces total test time. The trade-off is that container state is shared across all test classes in a run; tests must be written to avoid index-name collisions. `TestHelper` appends a caller-supplied suffix to entity definition names for exactly this reason.

- **Context refresh per class.** `@DirtiesContext(classMode = AFTER_CLASS)` on each base class causes Spring to destroy and rebuild the application context between test classes. This ensures no Spring bean state leaks between test classes while still allowing containers to remain running across the full test suite.

- **Migration container polling.** `KinoticTestConfiguration` polls the Docker daemon directly using `DockerClientFactory` to detect when the `kinotic-migration` container exits, rather than relying on a Docker health check. The polling budget is 10 minutes, reflecting that migration may involve creating large numbers of Elasticsearch indices against a freshly started container.

- **ARM/Apple Silicon compatibility.** Both `ElasticsearchTestConfiguration` (Testcontainers) and `KinoticTestConfiguration` (Docker Compose, via `compose.ek-m4.override.yml`) detect `os.arch=aarch64` on macOS and apply platform-specific workarounds. The Elasticsearch container receives `-XX:UseSVE=0` to work around a JDK/SVE interaction on Apple Silicon hardware.

- **Keycloak port layout.** Keycloak 26 (Quarkus distribution) separates application traffic (port 8888, configured via `KC_HTTP_PORT`) from management/health traffic (port 9000). Both ports must be exposed. The Testcontainers wait strategy targets port 9000 (`/health/ready`); application and token URLs use port 8888.

- **`DummySecurityService` credential scope.** The dummy service accepts only the literal credentials `guest/guest` via either STOMP `login`/`passcode` or HTTP Basic `authorization`. Any other credential returns a failed `CompletableFuture`. It is guarded by `@ConditionalOnProperty` and is inactive whenever `oidc-security-service.enabled=true`.

- **Compose file location.** `KinoticTestConfiguration` resolves compose files relative to `../deployment/docker-compose/` from the Gradle working directory (the `kinotic-test` module directory). This path must exist within the mono-repo layout; running the full-stack tests outside the repo root will fail.

- **`commons-io` forced version.** The build explicitly pins `commons-io:2.21.0` as a `testImplementation` dependency to avoid a `NoSuchMethodError` at runtime when Testcontainers copies files into containers using `commons-compress`, which requires a newer version of `commons-io` than the default transitive dependency graph provides.

- **Test timeout.** The Gradle `test` task is configured with a 10-minute timeout to accommodate slow container startups, large migrations, and Elasticsearch index stabilisation delays.
