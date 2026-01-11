# Structures Server

A Spring Boot server application that provides REST API, GraphQL, and web interface access to the Structures framework.

## Overview

Structures Server is a complete server application that exposes the Structures Core library through:
- **REST API**: Comprehensive REST endpoints for all data operations
- **GraphQL API**: Full GraphQL interface with schema introspection
- **Web Interface**: Built-in web GUI for data management
- **Health Checks**: Application health monitoring and metrics

## Features

- **REST API**: Complete CRUD operations via REST endpoints
- **GraphQL API**: Native GraphQL support with playground
- **Web GUI**: Built-in web interface for data management
- **Multi-tenant Support**: Tenant isolation and management
- **Authentication**: OIDC, JWT, and basic authentication support
- **CORS Support**: Configurable cross-origin resource sharing
- **Health Monitoring**: Application health and readiness checks
- **Static File Serving**: Web asset hosting capabilities

## Quick Start

### 1. Build the application

```bash
./gradlew :structures-server:build
```

### 2. Run the server

```bash
./gradlew :structures-server:bootRun
```

### 3. Access the services

- **Web Interface**: http://localhost:9090
- **GraphQL Playground**: http://localhost:4000/graphql
- **REST API**: http://localhost:8080/api/
- **Health Check**: http://localhost:9090/health/

## Configuration

### Server Configuration
```yaml
structures:
  web-server:
    port: 9090
    enable-static-file-server: true
    health-check-path: /health/
  
  open-api:
    port: 8080
    path: /api/
    server-url: http://127.0.0.1:8080
    security-type: BASIC
  
  graphql:
    port: 4000
    path: /graphql/
  
  cors:
    allowed-origin-pattern: "*"
```

### Data Source Configuration
```yaml
structures:
  elasticsearch:
    connections:
      - scheme: http
        host: elasticsearch
        port: 9200
        username: elastic
        password: changeme
    connection-timeout: 5s
    socket-timeout: 60s
    index-prefix: struct_
    tenant-id-field: structuresTenantId
```

### Authentication Configuration
```yaml
structures:
  auth:
    oidc:
      enabled: true
      allowed-issuers:
        - "https://sts.windows.net"
        - "https://your-okta-domain.okta.com"
    basic-auth:
      enabled: true
```

## API Endpoints

### REST API (`/api/`)
- `GET /api/structures/{tenantId}/{schemaId}` - List structures
- `POST /api/structures/{tenantId}/{schemaId}` - Create structure
- `GET /api/structures/{tenantId}/{schemaId}/{id}` - Get structure
- `PUT /api/structures/{tenantId}/{schemaId}/{id}` - Update structure
- `DELETE /api/structures/{tenantId}/{schemaId}/{id}` - Delete structure
- `GET /api/schemas/{tenantId}` - List schemas
- `POST /api/schemas/{tenantId}` - Create schema

### GraphQL API (`/graphql/`)
- **Query**: `structures`, `schemas`, `searchStructures`
- **Mutation**: `createStructure`, `updateStructure`, `deleteStructure`
- **Subscription**: Real-time updates (if configured)

### Health Endpoints
- `GET /health/` - Application health status
- `GET /health/readiness` - Readiness probe
- `GET /health/liveness` - Liveness probe

## Development

### Running in Development Mode
```bash
# Start with hot reload
./gradlew :structures-server:bootRun --args='--spring.profiles.active=dev'

# Run with specific configuration
./gradlew :structures-server:bootRun --args='--server.port=9091'
```

### Testing
```bash
# Run all tests
./gradlew :structures-server:test

# Run specific test class
./gradlew :structures-server:test --tests *StructureControllerTest
```

## Docker Deployment

### Build Docker Image
```bash
./gradlew :structures-server:dockerBuild
```

### Run with Docker Compose
```bash
cd docker-compose
docker-compose up -d
```

## Production Considerations

### Security
- Enable HTTPS in production
- Configure proper CORS policies
- Use secure authentication methods
- Implement rate limiting

### Performance
- Configure connection pooling
- Enable response caching
- Monitor Elasticsearch performance
- Use load balancing for high availability

### Monitoring
- Enable metrics collection
- Configure logging aggregation
- Set up health check monitoring
- Monitor resource usage

## Troubleshooting

### Common Issues

1. **Port Conflicts**
   - Check if ports 8080, 4000, or 9090 are already in use
   - Configure different ports in application.yml

2. **Elasticsearch Connection**
   - Verify Elasticsearch is running and accessible
   - Check connection credentials and network access

3. **Authentication Issues**
   - Verify OIDC provider configuration
   - Check JWT token validity and claims

### Debug Mode
Enable debug logging for troubleshooting:

```yaml
logging:
  level:
    org.mindignited.structures: DEBUG
    org.springframework.security: DEBUG
```

## Related Documentation

- [Structures Core](../structures-core/README.md) - Core library documentation
- [Structures Auth](../structures-auth/README.md) - Authentication documentation
- [Getting Started Guide](../../webdocs/guide/getting-started.md) - Complete setup guide

## License

This application is part of the Structures framework and follows the same licensing terms.
