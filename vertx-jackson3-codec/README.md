# vertx-jackson3-codec

A configurable Vert.x JSON codec backed by a Jackson 3 (`tools.jackson`) `ObjectMapper`.

## Why

Vert.x 5 ships a Jackson 3 codec (`io.vertx.core.json.jackson.v3.DatabindCodec`), but its `ObjectMapper` is
built internally and — because Jackson 3 mappers are immutable — cannot be configured at all
([eclipse-vertx/vert.x#6168](https://github.com/eclipse-vertx/vert.x/issues/6168)). The Jackson 2 approach of
grabbing `DatabindCodec.mapper()` and registering modules on it no longer exists.

This module replaces that codec via Vert.x's own `io.vertx.core.spi.JsonFactory` SPI. It behaves like the
built-in Jackson 3 codec — same Vert.x data-binding conventions, same RFC-7493 wire encodings — but the
mapper is built from a customizable builder that you can fully configure.

A side benefit: the built-in Jackson 3 codec only exists in `META-INF/versions/21` of `vertx-core`, which
some source-processing tools (javadoc, delombok) cannot resolve. This module's classes are ordinary classes.

## Requirements

- Java 17+ (the Jackson 3 baseline; Vert.x 5 itself runs on 11)
- Vert.x 5.x
- Jackson 3 (`tools.jackson.core:jackson-databind`)

On Java 17-20 this module is the only way to use Jackson 3 with Vert.x JSON at all: the built-in Jackson 3
codec lives in `META-INF/versions/21` of `vertx-core`, so Vert.x never activates it on those runtimes.

## Usage

Add the dependency and you are done — the codec registers itself through `META-INF/services`, and any
registered `JsonFactory` takes precedence over the codec Vert.x falls back to on its own.

### Gradle

```groovy
dependencies {
    implementation 'org.kinotic:vertx-jackson3-codec:<version>'
}
```

### Maven

```xml
<dependency>
    <groupId>org.kinotic</groupId>
    <artifactId>vertx-jackson3-codec</artifactId>
    <version>${vertx-jackson3-codec.version}</version>
</dependency>
```

## Configuring the mapper

Implement `Jackson3MapperCustomizer` and register it as a service. Every registered customizer is applied to
the mapper's builder once, when the codec first loads.

```java
package com.example;

import org.kinotic.vertx.jackson3.Jackson3MapperCustomizer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.cfg.ConstructorDetector;

public class MyMapperCustomizer implements Jackson3MapperCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.constructorDetector(ConstructorDetector.EXPLICIT_ONLY)
               .addModule(new MyDomainModule());
    }
}
```

`src/main/resources/META-INF/services/org.kinotic.vertx.jackson3.Jackson3MapperCustomizer`:

```
com.example.MyMapperCustomizer
```

The order between multiple registered customizers is unspecified, so independent customizers must not
contradict each other. Anything the builder exposes is fair game: modules, mapper features, constructor
detection, visibility, stream read constraints, and so on.

The mapper in use is readable via `VertxJackson3Codec.mapper()`, mirroring the accessor the Jackson 2 codec
offered.

## Providing the complete mapper

When a framework already manages a fully configured mapper, install it wholesale instead of customizing the
default one. A Spring Boot application, for example, can hand Vert.x the Boot-managed mapper — which already
carries every `JacksonModule` bean in the context:

```java
@Bean
public VertxJackson3Module vertxJackson3Module() {
    // Boot folds JacksonModule beans into its mapper, keeping the Vert.x types on their wire format
    return new VertxJackson3Module();
}
```

```java
// during startup, before JSON traffic flows:
VertxJackson3Codec.setMapper(springManagedJsonMapper);
```

The supplied mapper replaces the default for all subsequent codec operations and must carry a
`VertxJackson3Module` for the Vert.x types to keep their wire format.

## Building custom mappers that stay Vert.x-compatible

`VertxJackson3Module` is public and reusable. If you build separate mappers (for HTTP clients, storage, etc.)
that must produce the same wire format as Vert.x JSON — `JsonObject`, `JsonArray`, `Buffer` and `byte[]` as
RFC-7493 base64url, `Instant` as ISO-8601 — register it on your own builder:

```java
JsonMapper mapper = JsonMapper.builder()
                              .addModule(new VertxJackson3Module())
                              .build();
```

## License

The codec and serializers are derived from Eclipse Vert.x sources and remain under their original
`EPL-2.0 OR Apache-2.0` dual license (see the file headers). The rest of the repository is licensed
separately; see the repository root `LICENSE.txt`.
