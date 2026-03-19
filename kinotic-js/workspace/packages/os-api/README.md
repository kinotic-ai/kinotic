# @kinotic-ai/os-api

The high-level TypeScript API for managing everything in your Kinotic cloud OS. `@kinotic-ai/os-api` puts the full power of the platform in your hands — create applications, define data schemas, run migrations, manage queries, and monitor your system, all from code. It is the control plane for teams that want to automate and programmatically manage their Kinotic environment end to end.

**[Get started at kinotic.ai/getting-started](https://kinotic.ai/getting-started)**

> **Who is this for?** `@kinotic-ai/os-api` is intended for teams **building or extending Kinotic OS itself** — platform engineers managing the control plane, provisioning tooling, or internal infrastructure. If you are building an application *on top of* Kinotic OS, you most likely want [`@kinotic-ai/persistence`](https://www.npmjs.com/package/@kinotic-ai/persistence) and [`@kinotic-ai/core`](https://www.npmjs.com/package/@kinotic-ai/core) instead.

---

## Why @kinotic-ai/os-api?

- **Manage your whole cloud OS from code.** Applications, projects, schemas, migrations, and analytics are all first-class operations — no console required.
- **Ship data APIs in seconds.** Publish a schema at runtime and the platform creates storage, REST endpoints, and a GraphQL schema automatically.
- **Migrations as code.** Run and track schema migrations programmatically, not through ad-hoc scripts or manual steps.
- **One line to activate.** Plugs directly into your existing Kinotic connection — no additional setup, no separate configuration.
- **Full observability built in.** Structured logging and built-in analytics give you visibility into your applications without extra tooling.

---

## Features

### Application and Project Management
Create, configure, and manage cloud applications and projects programmatically. Automate provisioning as part of your deployment pipeline rather than through manual console operations.

### Runtime Schema Publishing
Define and publish new data schemas at runtime. The moment a schema is published, the platform creates the backing storage, generates REST API endpoints, and builds the corresponding GraphQL schema — all automatically.

### Schema Migrations
Run and track schema migrations from your application code. Migration state is managed by the platform, so you always know what has been applied and what is pending.

### Named Query Management
Define, publish, and manage reusable parameterized queries across your platform. Queries are platform-level resources — shared, versioned, and callable by any service.

### Analytics and Data Insights
Built-in analytics give you operational visibility into your applications and data without instrumenting your own pipeline. Query usage, data trends, and system health are available directly through the API.

### Structured Application Logging
Emit structured, searchable logs from the client side using the same logging infrastructure the platform uses. No separate log aggregation setup required.

---

## Part of the Kinotic Ecosystem

`@kinotic-ai/os-api` sits at the top of the Kinotic JavaScript/TypeScript stack:

```
@kinotic-ai/os-api      ← cloud OS management (this package)
@kinotic-ai/persistence ← schema-driven data access
@kinotic-ai/idl         ← cross-language type schemas
@kinotic-ai/core        ← connection & transport
```

It builds on the full stack below it. Install `@kinotic-ai/core` for the connection, add `@kinotic-ai/idl` and `@kinotic-ai/persistence` for data access, and use `@kinotic-ai/os-api` when you need to manage the platform itself.

---

## Installation

```bash
npm install @kinotic-ai/os-api
```

For full documentation, guides, and API reference, visit **[kinotic.ai](https://kinotic.ai)**.
