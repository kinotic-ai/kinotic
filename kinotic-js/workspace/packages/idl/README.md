# @kinotic-ai/idl

The type schema layer that makes Kinotic's cross-language RPC safe and consistent. `@kinotic-ai/idl` bridges Java backend services and TypeScript clients so you always know exactly what you're sending and receiving — no guesswork, no runtime type surprises. It is the foundation Kinotic uses to generate APIs, validate data, and keep every part of the platform speaking the same language.

**[Get started at kinotic.ai/getting-started](https://kinotic.ai/getting-started)**

---

## Why @kinotic-ai/idl?

- **One schema, every language.** Define a data structure or service interface once in a language-neutral format — Java, TypeScript, and the platform all stay in sync automatically.
- **No more integration surprises.** Schema contracts are enforced across the RPC boundary, catching mismatches at definition time rather than in production.
- **Rich metadata, zero boilerplate.** Attach validation rules and behavioral decorators to your models without writing custom validators or annotation processors.
- **Drives the whole platform.** OpenAPI specifications, GraphQL schemas, and serialization logic are all generated from the same IDL definitions — change once, update everywhere.

---

## Features

### Language-Neutral Schema Definition
Describe any data structure or service interface in a format that is not tied to any one language or runtime. The same definition drives both the Java server and the TypeScript client.

### Automatic API Generation
Publish a schema and the platform generates OpenAPI endpoints, GraphQL types, and serialization mappings for you. There is no manual mapping layer to maintain.

### Validation and Behavioral Metadata
Annotate data models with constraints and behavioral hints directly in the schema. Validation rules travel with the definition instead of being scattered across your codebase.

### Cross-Platform Serialization
Data moving between services is serialized and deserialized according to the schema contract. Format differences between languages are handled automatically.

### Foundation for Schema-Driven Development
`@kinotic-ai/idl` is the type layer that everything else in the Kinotic platform builds on. Define your domain model here and let the platform do the rest.

---

## Part of the Kinotic Ecosystem

`@kinotic-ai/idl` sits just above the core transport layer:

```
@kinotic-ai/os-api      ← cloud OS management
@kinotic-ai/persistence ← schema-driven data access
@kinotic-ai/idl         ← cross-language type schemas (this package)
@kinotic-ai/core        ← connection & transport
```

`@kinotic-ai/persistence` and `@kinotic-ai/os-api` both depend on the schema definitions this package provides. Start here when you need to describe data that crosses a service boundary.

---

## Installation

```bash
npm install @kinotic-ai/idl
```

For full documentation, guides, and API reference, visit **[kinotic.ai](https://kinotic.ai)**.
