# @kinotic-ai/persistence

Instant, schema-driven data access for your Kinotic applications. `@kinotic-ai/persistence` eliminates the data layer boilerplate that slows every project down — define your entity once and get full CRUD, full-text search, pagination, bulk operations, and named queries without writing a single query or mapping file. It is designed for developers who want enterprise-grade data capabilities without the infrastructure overhead.

**[Get started at kinotic.ai/getting-started](https://kinotic.ai/getting-started)**

---

## Why @kinotic-ai/persistence?

- **Zero query boilerplate.** Decorate your TypeScript classes and the data layer is ready — no mappers, no repositories, no hand-written queries to maintain.
- **Search is a first-class feature.** Full-text search across your data is available immediately, as naturally as any other read operation.
- **Built for scale.** Pagination, bulk operations, and cursor-based traversal handle large datasets without architectural changes.
- **Multi-tenancy handled for you.** Data isolation between tenants is automatic — your application logic stays clean.
- **One line to activate.** Plugs directly into your existing Kinotic connection. No separate configuration, no extra infrastructure.

---

## Features

### Decorator-Driven Entity Definition
Annotate your TypeScript classes with decorators from this package to define entities — ID generation, indexing behavior, precision, multi-tenancy, access policies, and more. Once decorated, the full data access layer — reads, writes, deletes, and queries — is available immediately. No separate schema files, no mapping layer to write or maintain.

### Full CRUD and Bulk Operations
Create, read, update, and delete individual records or entire batches with a consistent API. Bulk operations are designed for high-throughput scenarios without special-casing.

### Full-Text Search
Query your data with powerful full-text search out of the box. No separate search infrastructure required — it comes with the persistence layer.

### Flexible Pagination
Retrieve large result sets with both offset-based and cursor-based pagination. Switch between styles depending on the use case without changing your data model.

### Named and Reusable Queries
Define complex queries once, give them a name, and reuse them across your application. Query definitions are first-class citizens, not one-off strings scattered through your code.

### Multi-Tenancy and Data Isolation
Tenant boundaries are enforced automatically. Data from one tenant never leaks into another — no conditional filters to add, no policies to wire up manually.

### Optimistic Locking
Concurrent writes are safe by default. Optimistic locking prevents silent data conflicts without requiring pessimistic locks or complex transaction management.

---

## Part of the Kinotic Ecosystem

`@kinotic-ai/persistence` sits in the middle of the Kinotic JavaScript/TypeScript stack:

```
@kinotic-ai/os-api      ← cloud OS management
@kinotic-ai/persistence ← schema-driven data access (this package)
@kinotic-ai/idl         ← cross-language type schemas
@kinotic-ai/core        ← connection & transport
```

It depends on `@kinotic-ai/core` for the platform connection and `@kinotic-ai/idl` for schema definitions. `@kinotic-ai/os-api` builds on top of it to provide full cloud OS management.

---

## Installation

```bash
npm install @kinotic-ai/persistence
```

For full documentation, guides, and API reference, visit **[kinotic.ai](https://kinotic.ai)**.
