# kinotic-idl

A language-neutral Interface Definition Library that models service contracts and entity schemas as a portable, Jackson-serializable type graph.

## Overview

Service-oriented systems often need to describe their types and operations in a form that can be understood by tools written in languages other than Java. Without a shared, serializable representation, every consumer must independently parse Java reflection data, invent its own encoding, and duplicate the logic that resolves generics, handles inheritance, and maps primitives. `kinotic-idl` solves this by providing a single, authoritative object model — the **C3 type system** — that captures every structural detail that downstream code generators and protocol bridges need.

The module bridges two concerns: **schema introspection** and **schema conversion**. The introspection side (`SchemaFactory`) walks a Java class or interface at runtime and produces a fully resolved `NamespaceDefinition` containing typed service and object definitions. The conversion side (`IdlConverterFactory` / `IdlConverterStrategy`) accepts that definition and applies a caller-supplied strategy to emit any target representation — TypeScript type stubs, JSON Schema documents, wire-format descriptors, or anything else.

Within the broader Kinotic architecture, `kinotic-idl` is the cross-language type bridge. Modules that expose services or define entity schemas depend on this library to produce a canonical, wire-safe description that other Kinotic modules can consume without tight coupling to the JVM type system.

The library is a pure Spring Boot auto-configured library. It contributes its Spring beans automatically via `KinoticIdlAutoConfiguration` and requires no manual configuration in the typical case.

## Key Concepts

- **`C3Type`** — Abstract base class for every type node in the graph. Jackson `@JsonTypeInfo` / `@JsonSubTypes` annotations make the full type hierarchy serializable to and from JSON by name, enabling the schema to cross process and language boundaries.

- **`ComplexC3Type`** — Abstract subclass of `C3Type` that adds `namespace`, `name`, `decorators`, and `metadata` fields. It is the base for the three user-defined type shapes: `ObjectC3Type`, `EnumC3Type`, and `UnionC3Type`. All three implement `HasQualifiedName` so their fully qualified names can be resolved at runtime.

- **`ReferenceC3Type`** — A type node that carries only a `qualifiedName` string. It is emitted when the schema factory encounters a complex type that has already been registered in the current conversion context, allowing the graph to represent cycles and shared definitions without infinite recursion.

- **`NamespaceDefinition`** — The root document produced by `SchemaFactory.createForService`. It groups a `Set<ComplexC3Type>` (all object and enum types discovered while walking the service) with a `Set<ServiceDefinition>` (one per processed interface), forming a self-contained, fully resolved schema unit.

- **`ServiceDefinition`** / **`FunctionDefinition`** / **`ParameterDefinition`** — A three-level hierarchy that models a Java interface. A `ServiceDefinition` has a namespace, a name, and an ordered set of `FunctionDefinition`s. Each function records its return `C3Type` and an ordered list of `ParameterDefinition`s, each of which carries a name and a `C3Type`.

- **`C3Decorator`** — An extensible annotation mechanism that can be attached to types, properties, functions, or parameters. Decorators are identified by a string type discriminator and are fully serializable. The built-in decorator is `NotNullC3Decorator`. Custom decorators are added by extending `C3Decorator` and registering the subtype with Jackson.

- **`SchemaFactory`** — The primary entry point for schema introspection. `createForClass(Class<?>)` produces a `C3Type` for a POJO or primitive. `createForService(Class<?>)` produces a `NamespaceDefinition` for an interface, walking every user-declared method via Spring's `ResolvableType` to handle generic type parameters correctly.

- **`IdlConverterStrategy`** / **`IdlConverter`** / **`C3TypeConverter`** — The three-layer conversion pipeline. A strategy bundles a set of stateless `C3TypeConverter` instances plus an `initialState()` factory. `IdlConverterFactory.createConverter(strategy)` wraps the strategy in a stateful `IdlConverter`. Each `C3TypeConverter.convert(c3Type, context)` can recurse back into the context to convert nested types. If a converter additionally implements `Cacheable` and the strategy enables caching, conversion results are memoized within the converter's lifetime.

## Configuration

### Spring Boot auto-configuration

`kinotic-idl` registers itself via the standard Spring Boot auto-configuration mechanism. The file

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

lists `org.kinotic.idl_autoconfig.KinoticIdlAutoConfiguration`.

`KinoticIdlAutoConfiguration` is annotated with `@AutoConfiguration` and `@Import(KinoticIdlLibrary.class)`. It lives in the package `org.kinotic.idl_autoconfig` — deliberately outside `org.kinotic.idl` — so that an application's own component scan never picks it up twice.

`KinoticIdlLibrary` carries `@Configuration` and `@ComponentScan`, which activates all internal `@Component`-annotated beans under `org.kinotic.idl`.

### Spring beans registered automatically

| Bean | Type | Notes |
|---|---|---|
| `defaultSchemaFactory` | `DefaultSchemaFactory` | Primary `SchemaFactory` implementation |
| `defaultResolvableTypeConverter` | `DefaultResolvableTypeConverter` | `@Primary` composite converter; auto-wires all `ResolvableTypeConverter` beans |
| JDK type converters | Various `SpecificTypeConverter` `@Component`s | One per JDK type (Boolean, Byte, Char, Date, Double, Float, Integer, Iterable, Long, Map, Optional, Short, String, URI, Void, array, enum) |
| `defaultIdlConverterFactory` | `DefaultIdlConverterFactory` | Primary `IdlConverterFactory` implementation |

### Spring properties

This module does not define any `application.properties` / `application.yml` configuration keys. All behaviour is controlled programmatically through the `IdlConverterStrategy` interface.

## Usage Example

The following snippet introspects a Java service interface, then converts the resulting `NamespaceDefinition` to a simple string representation using a custom strategy.

```java
// 1. Obtain beans (injected by Spring)
@Autowired SchemaFactory schemaFactory;
@Autowired IdlConverterFactory converterFactory;

// 2. Introspect a service interface into a NamespaceDefinition
NamespaceDefinition ns = schemaFactory.createForService(OrderService.class);

// 3. Build a strategy that converts each C3Type to a display string
C3TypeConverterContainer<String, Void> container = new C3TypeConverterContainer<String, Void>()
    .addConverter(StringC3Type.class,  (t, ctx) -> "string")
    .addConverter(IntC3Type.class,     (t, ctx) -> "int")
    .addConverter(ObjectC3Type.class,  (t, ctx) -> t.getQualifiedName())
    .addConverter(ArrayC3Type.class,   (t, ctx) -> "array<" + ctx.convert(t.getContains()) + ">")
    .addConverter(VoidC3Type.class,    (t, ctx) -> "void");

IdlConverterStrategy<String, Void> strategy = new IdlConverterStrategy<>() {
    public Set<C3TypeConverter<String, ? extends C3Type, Void>> converters() {
        return Set.of(container);
    }
    public Void initialState()   { return null; }
    public boolean shouldCache() { return true; }
};

// 4. Convert function return types to strings
IdlConverter<String, Void> converter = converterFactory.createConverter(strategy);

ns.getServices().stream()
    .flatMap(svc -> svc.getFunctions().stream())
    .forEach(fn -> System.out.println(
        fn.getName() + " -> " + converter.convert(fn.getReturnType())));
```

## Notes

- **Auto-configuration isolation.** `KinoticIdlAutoConfiguration` resides in `org.kinotic.idl_autoconfig` rather than `org.kinotic.idl`. This is intentional: if an application already has a component scan rooted at a parent of `org.kinotic.idl`, the auto-configuration class must not be scanned as an ordinary `@Configuration` bean or it would be processed twice. The separate package prevents that.

- **Converter priority ordering.** `DefaultResolvableTypeConverter` resolves types by consulting `SpecificTypeConverter`s first (keyed by exact `Class.getName()`), then iterating `GenericTypeConverter`s in registration order. `PojoTypeConverter` is always added last in `DefaultResolvableTypeConverter`'s constructor — not via `@Order` — because ordering via annotation was unreliable in practice.

- **`Optional` unwrapping.** `OptionalTypeConverter` transparently unwraps `Optional<T>` by resolving its first type argument and delegating back to the context. The schema never contains an `Optional` node; it always contains the wrapped type directly.

- **Groovy class filtering.** `PojoTypeConverter` explicitly skips properties declared on `GroovyObject` and `MetaClass` in addition to `Object`. This is required because Groovy classes expose `getMetaClass()` and related methods as `BeanInfo` properties, which would pollute every schema that originates from a Groovy POJO.

- **Jackson polymorphism.** `C3Type` and `C3Decorator` both use `@JsonTypeInfo(use = Id.NAME)` with a `"type"` property discriminator and explicit `@JsonSubTypes` lists. Custom subtypes must be registered with the application's `JsonMapper` (e.g., via a custom module or additional `@JsonSubTypes` entries on the base class) before they can be round-tripped through JSON.

- **Conversion context state lifetime.** An `IdlConverter` instance is stateful: it holds a single `C3ConversionContext` for its entire lifetime. The cache inside the context grows monotonically. When conversion state must be reset — for example, to process an unrelated schema without cache interference — a new `IdlConverter` must be obtained from `IdlConverterFactory.createConverter`.

- **`@Name` and parameter name retention.** Java erases parameter names at compile time unless `-parameters` is passed to `javac`. `DefaultSchemaFactory` falls back to `@Name` annotation values when the parameter name is unavailable at runtime. Callers that rely on named parameters in the produced schema should either compile with `-parameters` or annotate every parameter with `@Name`.

- **Decorator serialization.** `C3Decorator.type` and `C3Decorator.targets` are both annotated `@JsonIgnore`. The `type` discriminator is written by Jackson's `@JsonTypeInfo` mechanism, not as a plain field. Implementations must set `this.type` and `this.targets` in their no-arg constructor because Jackson uses that constructor during deserialization.
