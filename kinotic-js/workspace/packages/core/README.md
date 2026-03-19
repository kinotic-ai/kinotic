# @kinotic-ai/core

The foundational TypeScript/JavaScript client for the Kinotic platform. `@kinotic-ai/core` handles everything below the application layer — connection management, service discovery, and message routing — so your code can talk to any Kinotic service without thinking about the underlying transport. Whether you're building a browser app, a Node.js service, or a CLI tool, this package is the single entry point to the entire Kinotic ecosystem.

**[Get started at kinotic.ai/getting-started](https://kinotic.ai/getting-started)**

---

## Why @kinotic-ai/core?

- **One connection, entire platform.** A single object gives you access to every service registered on Kinotic — no configuration sprawl, no per-service clients to manage.
- **Remote calls that feel local.** Invoke distributed services the same way you call any function — no REST boilerplate, no manual HTTP wiring, no endpoint management.
- **Real-time by default.** Built-in reactive streaming lets you subscribe to live data flows as naturally as reading a list.
- **Uniform resource addressing.** Every resource on the platform — local or clustered — is reachable through a consistent addressing scheme. Move services around without changing calling code.
- **Built to be extended.** Higher-level packages like `@kinotic-ai/persistence` and `@kinotic-ai/os-api` plug directly into the connection this package provides.

---

## Features

### Single-Object Platform Access
Connect once. From that connection you can discover, call, and stream any service on the Kinotic platform — nothing else required.

### Transparent Remote Procedure Calls
Call distributed backend services as if they were in-process. The transport, serialization, and routing are completely invisible to your application code.

### Reactive Streaming
Subscribe to event streams and live data feeds with a clean, composable API. Handle real-time updates, push notifications, and continuous data flows without callbacks or polling.

### Consistent Resource Addressing
Every service, stream, and resource in Kinotic has a stable, uniform address. Your application references resources the same way regardless of where they run in the cluster.

### Distributed Tracing
Request tracing is built in at the transport layer. Every call carries context automatically, making distributed systems observable without extra instrumentation.

### Extensible Foundation
The connection model is designed to be consumed by higher-level packages. Persistence, schema management, and OS-level APIs all build on the primitives `@kinotic-ai/core` provides.

---

## Part of the Kinotic Ecosystem

`@kinotic-ai/core` sits at the base of the Kinotic JavaScript/TypeScript stack:

```
@kinotic-ai/os-api      ← cloud OS management
@kinotic-ai/persistence ← schema-driven data access
@kinotic-ai/idl         ← cross-language type schemas
@kinotic-ai/core        ← connection & transport (this package)
```

Every other package in the ecosystem depends on `@kinotic-ai/core`. Install it first; the rest plug in on top.

---

## Installation

```bash
npm install @kinotic-ai/core
```

For full documentation, guides, and API reference, visit **[kinotic.ai](https://kinotic.ai)**.
