# Structures Server Config

Structures is designed to be embedded in any Spring Boot project. You can configure it using any standard Spring configuration mechanism, such as `application.yml`, `application.properties`, or environment variables. The reference implementation is in the `@structures-server` module, but you can use these options in your own Spring Boot applications by adding the `@EnableStructures` annotation to your main class.

## Quick Start: Reference Server & Docker Compose

To quickly get started, you have two main options:

### 1. Run the Reference Structures Server
You can run the reference implementation directly:

- Clone the repository
- Build and run the `structures-server` module:
  ```sh
  ./gradlew :structures-server:bootRun
  ```
- This will start the server with default configuration. You can customize it using the configuration options below.

### 2. Use Docker Compose
A set of Docker Compose files is provided in the `docker-compose` folder to help you spin up Structures and its dependencies easily:

- Navigate to the `docker-compose` directory:
  ```sh
  cd docker-compose
  ```
- Start the stack (for example, the default stack):
  ```sh
  docker compose -f compose.yml up
  ```
- You can find additional compose files for different setups (e.g., `compose.ek-stack.yml`, `compose-otel.yml`).
- Adjust the configuration as needed in the YAML files or by overriding environment variables.

---

## Creating Your Own Structures Server

You can create your own custom Structures server by embedding Structures in your own Spring Boot application. This gives you full control over authentication, authorization, and other aspects of the system.

Add the following annotation to your Spring Boot application:

```java
import org.kinotic.persistence.api.annotations.EnableStructures;

@SpringBootApplication
@EnableStructures
public class MyStructuresServer {
    public static void main(String[] args) {
        SpringApplication.run(MyStructuresServer.class, args);
    }
}
```

### Customizing Authentication

You can provide your own implementation of the `SecurityService` interface to control how users are authenticated. For example, the reference server includes a simple hardcoded implementation:

```java
// src/main/java/org/kinotic/structuresserver/config/TemporarySecurityService.java
@Component
public class TemporarySecurityService implements SecurityService {
    private static final String PASSWORD = "structures";
    @Override
    public CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo) {
        if(authenticationInfo.containsKey("login") && Objects.equals(authenticationInfo.get("login"), "admin")
            && authenticationInfo.containsKey("passcode") && Objects.equals(authenticationInfo.get("passcode"), PASSWORD)){
            // Authenticated with hardcoded username/password
            return CompletableFuture.completedFuture(createParticipant(authenticationInfo.get("tenantId")));
        } else if (authenticationInfo.containsKey("authorization")) {
            String authorizationHeader = authenticationInfo.get("authorization");
            String[] parts = authorizationHeader.split(" ");
            if (parts.length == 2 && "Basic".equalsIgnoreCase(parts[0])) {
                String credentials = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                String[] creds = credentials.split(":", 2);
                if (creds.length == 2 && creds[0].equals("admin") && creds[1].equals(PASSWORD)) {
                    // Authenticated with HTTP Basic Auth
                    return CompletableFuture.completedFuture(createParticipant(authenticationInfo.get("tenantId")));
                }
            }
        }
        return CompletableFuture.failedFuture(new AuthenticationException("username/password pair provided was not correct."));
    }
    // ...
}
```

You can replace this logic with your own, for example to integrate with an external identity provider or custom authentication system.

### Customizing Authorization (RBAC)

For fine-grained authorization, you can implement your own `AuthorizationServiceFactory` to provide RBAC or other access control mechanisms. The interface looks like this:

```java
// org.kinotic.structures.api.services.security.AuthorizationServiceFactory
public interface AuthorizationServiceFactory {
    CompletableFuture<AuthorizationService<EntityOperation>> createStructureAuthorizationService(Structure structure);
    CompletableFuture<AuthorizationService<NamedQueryOperation>> createNamedQueryAuthorizationService(FunctionDefinition namedQuery);
}
```

By providing your own bean of this type, you can control authorization for all structure and query operations. See the `AuthorizationService` interface for details on how to implement authorization logic.

---

## Configuration Options

Structures uses Spring Boot's configuration system, which means you can set these options in your application's `application.yml`, `application.properties`, or via environment variables. All options are prefixed with `structures.`. For most users, the easiest way is to add them to your `application.yml` file.

> **Tip:** If you're new to Spring Boot configuration, see the [Spring Boot Externalized Configuration documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#application-properties) for more details on how properties are loaded and overridden.

Below are the available options, their types, and default values. When you see a YAML example, it should be placed in your Spring Boot project's `application.yml` file (usually in `src/main/resources`).

### Indexing and Tenant
- **indexPrefix** (`String`, default: `struct_`):
  Prefix for all Elasticsearch indices. Must be a valid namespace (letters, numbers, `.`, `_`, `-`, max 16 chars).
- **tenantIdFieldName** (`String`, default: `tenantId`):
  Field name used for tenant identification.

### Elasticsearch Connection
- **elasticConnections** (`List<ElasticConnectionInfo>`, default: one entry with `host: localhost`, `port: 9200`, `scheme: http`):
  List of Elasticsearch connection info. Each entry has:
  - `host` (`String`, default: `localhost`)
  - `port` (`int`, default: `9200`)
  - `scheme` (`String`, default: `http`)
- **elasticUsername** (`String`, default: `null`):
  Username for Elasticsearch (optional).
- **elasticPassword** (`String`, default: `null`):
  Password for Elasticsearch (optional).
- **elasticConnectionTimeout** (`Duration`, default: `5s`):
  Connection timeout for Elasticsearch.
- **elasticSocketTimeout** (`Duration`, default: `1m`):
  Socket timeout for Elasticsearch.
- **elasticHealthCheckInterval** (`Duration`, default: `1m`):
  Interval for health checks on the Elasticsearch cluster.

#### Example (`application.yml`):
```yaml
structures:
  elasticConnections:
    - host: "localhost"
      port: 9200
      scheme: "http"
  elasticUsername: "user"
  elasticPassword: "pass"
  elasticConnectionTimeout: 5s
  elasticSocketTimeout: 1m
  elasticHealthCheckInterval: 1m
```

### CORS (Cross-Origin Resource Sharing)
- **corsAllowedOriginPattern** (`String`, default: `http://localhost.*`):
  Java regex pattern for allowed origins. Use `*` to allow all.
- **corsAllowedHeaders** (`Set<String>`, default: `[Accept, Authorization, Content-Type]`):
  Allowed headers for CORS.
- **corsAllowCredentials** (`Boolean`, default: `null`):
  If set, controls the `Access-Control-Allow-Credentials` header. If `true`, origins must not contain `*`.

#### Example (`application.yml`):
```yaml
structures:
  corsAllowedOriginPattern: "https://studio.apollographql.com|http://localhost:\d+"
  corsAllowedHeaders:
    - "*"
  corsAllowCredentials: true
```

### HTTP Limits
- **maxHttpHeaderSize** (`int`, default: `8192`):
  Max length of all HTTP headers in bytes.
- **maxHttpBodySize** (`long`, default: `-1`):
  Max length of HTTP body in bytes. `-1` means no limit.

### OpenAPI and GraphQL
- **openApiSecurityType** (`String`, default: `NONE`):
  Security type for OpenAPI. Possible values:
  - `NONE`: No security
  - `BASIC`: Basic authentication
  - `BEARER`: Bearer token authentication
- **openApiPort** (`int`, default: `8080`):
  Port for OpenAPI server.
- **openApiPath** (`String`, default: `/api/`):
  Path for OpenAPI endpoints.
- **openApiAdminPath** (`String`, default: `/admin/api/`):
  Path for OpenAPI admin endpoints.
- **openApiServerUrl** (`String`, default: `http://localhost:8080`):
  Server URL for OpenAPI.
- **graphqlPort** (`int`, default: `4000`):
  Port for GraphQL server.
- **graphqlPath** (`String`, default: `/graphql/`):
  Path for GraphQL endpoint.

#### Example (`application.yml`):
```yaml
structures:
  openApiSecurityType: BASIC
  openApiPort: 8080
  openApiPath: "/api/"
  openApiAdminPath: "/admin/api/"
  openApiServerUrl: "http://localhost:8080"
  graphqlPort: 4000
  graphqlPath: "/graphql/"
```

### Web Server and Health Check
- **webServerPort** (`int`, default: `9090`):
  Port for static files and health check endpoint.
- **healthCheckPath** (`String`, default: `/health/`):
  Path for health check endpoint.
- **enableStaticFileServer** (`boolean`, default: `false`):
  If `true`, static files are served from `resources/webroot`.

### Sample Data
- **initializeWithSampleData** (`boolean`, default: `false`):
  If `true`, initializes Structures with sample data.

## Example Minimal Configuration (`application.yml`)

```yaml
structures:
  openApiSecurityType: BASIC
  enableStaticFileServer: true
```

## Example Development Configuration (`application.yml`)

```yaml
structures:
  initializeWithSampleData: true
  corsAllowedOriginPattern: "https://studio.apollographql.com|http://localhost:\d+"
  corsAllowedHeaders:
    - "*"
  corsAllowCredentials: true
```

## Notes
- All durations can be specified using the standard Spring Boot duration format (e.g., `5s`, `1m`).
- You can use any Spring Boot mechanism to supply these properties: YAML, properties files, environment variables, or command-line arguments.
- See the `@structures-server` module for a reference implementation and more configuration examples.