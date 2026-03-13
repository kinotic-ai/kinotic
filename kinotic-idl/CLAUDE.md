# kinotic-idl — AI Reference

> For narrative documentation see [README.md](./README.md).

## Build & Test Commands

```bash
./gradlew :kinotic-idl:build      # compile
./gradlew :kinotic-idl:test       # run unit tests
./gradlew :kinotic-idl:check      # build + test + lint
```

## Hard Rules

Directives for AI working on this module (sourced from architectural constraints in the Notes section and code):

- Never place auto-configuration classes inside `org.kinotic.idl` — always isolate them in `org.kinotic.idl_autoconfig` to prevent double-processing when a host application's component scan covers the main package.
- Never add `PojoTypeConverter` to `DefaultResolvableTypeConverter` via `@Order` or any annotation-based ordering — always register it last programmatically in the constructor, as annotation-based ordering is unreliable for this converter chain.
- Never emit an `Optional` node in the schema; always unwrap `Optional<T>` to its type argument so the schema only ever contains the wrapped type directly.
- Always skip properties declared on `GroovyObject` and `MetaClass` in `PojoTypeConverter` — do not add any code that would allow Groovy-internal methods to appear as schema properties.
- Never reuse an `IdlConverter` instance across unrelated schemas when caching is enabled — always obtain a fresh instance from `IdlConverterFactory.createConverter` to avoid cache interference between unrelated conversion runs.
- Always register custom `C3Type` or `C3Decorator` subtypes with the application's `JsonMapper` before round-tripping them through JSON; never assume they are auto-discovered.
- Always annotate or compile with `-parameters` when named parameters in the produced schema are required — never rely on runtime parameter name availability without one of these measures, and always fall back to `@Name` when names are unavailable.

## Package Structure

| Package | Role |
|---|---|
| `org.kinotic.idl.api.schema` | All `C3Type` subclasses, `NamespaceDefinition`, `ServiceDefinition`, `FunctionDefinition`, `ParameterDefinition`, `PropertyDefinition`, and the supporting interfaces (`HasQualifiedName`, `HasDecorators`, `HasMetadata`) |
| `org.kinotic.idl.api.schema.decorators` | `C3Decorator` base class, `DecoratorTarget` enum, and the built-in `NotNullC3Decorator` |
| `org.kinotic.idl.api.directory` | Public `SchemaFactory` interface |
| `org.kinotic.idl.api.converter` | Public converter contracts: `IdlConverter`, `IdlConverterFactory`, `IdlConverterStrategy`, `C3TypeConverter`, `C3ConversionContext`, `C3TypeConverterContainer`, and the `Cacheable` marker interface |
| `org.kinotic.idl.api.annotations` | `@Name` — a runtime parameter annotation for overriding the introspected parameter name |
| `org.kinotic.idl.internal.directory` | `DefaultSchemaFactory`, `DefaultResolvableTypeConverter` (composite), `PojoTypeConverter`, and the `ResolvableTypeConverter` / `SpecificTypeConverter` / `GenericTypeConverter` internal converter chain |
| `org.kinotic.idl.internal.directory.jdk` | JDK-type-specific `SpecificTypeConverter` implementations (primitives, boxed types, `String`, `Date`, `Map`, `Iterable`, arrays, `Optional`, `URI`, enums) |
| `org.kinotic.idl.internal.api.converter` | `DefaultIdlConverterFactory`, `DefaultIdlConverter`, `DefaultC3ConversionContext` |
| `org.kinotic.idl.internal.support.jsonSchema` | Internal JSON Schema POJO model used during schema serialization support |
| `org.kinotic.idl` | `KinoticIdlLibrary` — the `@Configuration` / `@ComponentScan` root |
| `org.kinotic.idl_autoconfig` | `KinoticIdlAutoConfiguration` — the Spring Boot auto-configuration class (isolated in its own package so it is not picked up by a component scan) |

## Operation Flow

**Introspection path:**

1. A caller injects `SchemaFactory` and calls `createForService(MyService.class)`.
2. `DefaultSchemaFactory` creates a `DefaultConversionContext` (service mode) backed by `DefaultResolvableTypeConverter`.
3. For each user-declared method on the interface, Spring `ResolvableType` is used to resolve the return type and each parameter type.
4. `DefaultResolvableTypeConverter` consults registered `SpecificTypeConverter`s first (exact JDK type match), then falls back to `PojoTypeConverter` for user-defined types.
5. `PojoTypeConverter` reads `BeanInfo` property descriptors and recursively converts each property, filtering out `Object`- and Groovy-internal methods. Complex types encountered for the first time are added to the conversion context's `complexC3Types` set; subsequent encounters emit a `ReferenceC3Type` pointing to the qualified name.
6. The resulting `NamespaceDefinition` contains the `ServiceDefinition` and all `ComplexC3Type`s reachable from it.

**Conversion path:**

1. A caller calls `IdlConverterFactory.createConverter(strategy)` with a custom `IdlConverterStrategy`.
2. The factory returns a `DefaultIdlConverter` wrapping a `DefaultC3ConversionContext` initialised from `strategy.initialState()`.
3. The caller calls `converter.convert(c3Type)` with a `C3Type` node.
4. `DefaultC3ConversionContext` locates the appropriate `C3TypeConverter` from the strategy's converter set via `supports(c3Type)`, invokes `convert(c3Type, context)`, and — if the converter implements `Cacheable` and the strategy enabled caching — stores the result keyed by the `C3Type` instance.
5. Converters that handle composite types (arrays, objects, services) recurse back into `context.convert(nestedType)`.
6. On error, a depth-stack trace is emitted at DEBUG level showing the full conversion path.

## Public API

| Type | Package | Description |
|---|---|---|
| `SchemaFactory` | `org.kinotic.idl.api.directory` | Introspects Java classes and interfaces into `C3Type` / `NamespaceDefinition` |
| `IdlConverterFactory` | `org.kinotic.idl.api.converter` | Spring bean; creates stateful `IdlConverter` instances from a strategy |
| `IdlConverter<R, S>` | `org.kinotic.idl.api.converter` | Stateful converter; call `convert(C3Type)` to emit target representation |
| `IdlConverterStrategy<R, S>` | `org.kinotic.idl.api.converter` | Caller-implemented; bundles `C3TypeConverter`s, `initialState()`, and `shouldCache()` |
| `C3TypeConverter<R, T, S>` | `org.kinotic.idl.api.converter` | Stateless converter for a single `C3Type` subtype; recurses through `C3ConversionContext` |
| `C3TypeConverterContainer<R, S>` | `org.kinotic.idl.api.converter` | Convenience `C3TypeConverter` that dispatches by `C3Type` class using lambda functions |
| `C3ConversionContext<R, S>` | `org.kinotic.idl.api.converter` | Passed to each `C3TypeConverter`; provides `convert(C3Type)` for recursion and `state()` for shared state |
| `Cacheable` | `org.kinotic.idl.api.converter` | Marker interface; add to a `C3TypeConverter` to enable result memoization when the strategy sets `shouldCache() = true` |
| `C3Type` | `org.kinotic.idl.api.schema` | Abstract root of the type hierarchy; Jackson-polymorphic |
| `ComplexC3Type` | `org.kinotic.idl.api.schema` | Abstract base for `ObjectC3Type`, `EnumC3Type`, `UnionC3Type` |
| `ObjectC3Type` | `org.kinotic.idl.api.schema` | Named POJO type with an ordered list of `PropertyDefinition`s and optional parent |
| `EnumC3Type` | `org.kinotic.idl.api.schema` | Named enumeration type with a list of string values |
| `UnionC3Type` | `org.kinotic.idl.api.schema` | Named type representing a discriminated union of `ObjectC3Type`s |
| `ArrayC3Type` | `org.kinotic.idl.api.schema` | Ordered sequence with a typed `contains` element |
| `MapC3Type` | `org.kinotic.idl.api.schema` | Key-value map with typed `key` and `value` elements |
| `ReferenceC3Type` | `org.kinotic.idl.api.schema` | Pointer to another type by `qualifiedName` |
| `NamespaceDefinition` | `org.kinotic.idl.api.schema` | Root document; holds a set of `ComplexC3Type`s and a set of `ServiceDefinition`s |
| `ServiceDefinition` | `org.kinotic.idl.api.schema` | Named interface with an ordered set of `FunctionDefinition`s |
| `FunctionDefinition` | `org.kinotic.idl.api.schema` | Named function with a return `C3Type` and an ordered list of `ParameterDefinition`s |
| `ParameterDefinition` | `org.kinotic.idl.api.schema` | Named, typed function parameter |
| `PropertyDefinition` | `org.kinotic.idl.api.schema` | Named, typed object property |
| `C3Decorator` | `org.kinotic.idl.api.schema.decorators` | Abstract, serializable annotation applied to types or members |
| `NotNullC3Decorator` | `org.kinotic.idl.api.schema.decorators` | Built-in decorator asserting non-null on fields and parameters |
| `@Name` | `org.kinotic.idl.api.annotations` | Runtime parameter annotation; overrides the introspected parameter name |

## Module Dependencies

| Dependency | Reason |
|---|---|
| `org.springframework.boot:spring-boot-starter` | Spring context, component scanning, `ResolvableType`, `BeanUtils`, `ReflectionUtils` |
| `com.fasterxml.jackson.core:jackson-annotations` | `@JsonTypeInfo`, `@JsonSubTypes`, `@JsonInclude`, `@JsonIgnore` on the type hierarchy |
| `tools.jackson.core:jackson-core` | Jackson 3 core serialization runtime |
| `tools.jackson.core:jackson-databind` | `@JsonDeserialize` and `JsonMapper` used internally |
| `org.apache.groovy:groovy` | `PojoTypeConverter` explicitly excludes Groovy-internal methods (`GroovyObject`, `MetaClass`) from property introspection |
| `io.freefair.lombok` (build-time) | Lombok code generation for all schema model classes |
