# Structures Core Library

The core library that provides the foundational data storage, retrieval, and management capabilities for the Structures framework.

## Overview

Structures Core is the heart of the Structures framework, providing:
- **Data Storage Engine**: Flexible schema-based data storage with support for multiple backends
- **Schema Management**: Dynamic schema evolution and versioning
- **Query Engine**: Powerful querying capabilities with GraphQL and REST support
- **Security Framework**: Authentication and authorization services
- **Plugin System**: Extensible architecture for custom functionality

## Features

- **Schema Evolution**: Dynamic schema changes without data migration
- **Multi-tenant Support**: Built-in tenant isolation and management
- **GraphQL API**: Native GraphQL support with schema introspection
- **REST API**: Comprehensive REST endpoints for all operations
- **Plugin Architecture**: Extensible system for custom data types and operations
- **Audit Trail**: Complete audit logging for all data operations
- **Search Integration**: Full-text search capabilities with Elasticsearch

## Dependencies

- **Spring Boot**: Core framework and dependency injection
- **GraphQL**: Query language and execution engine
- **Elasticsearch**: Search and indexing backend
- **Jackson**: JSON processing and serialization
- **Spring Security**: Authentication and authorization

## Testing

The library includes comprehensive tests:

```bash
./gradlew :structures-core:test
```

## Contributing

1. Follow the existing code conventions
2. Add tests for new functionality
3. Update documentation as needed
4. Ensure backward compatibility for schema changes

## License

This library is part of the Structures framework and follows the same licensing terms.
