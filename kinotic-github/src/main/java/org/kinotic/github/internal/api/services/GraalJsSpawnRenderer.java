package org.kinotic.github.internal.api.services;

import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders a spawn-format template tree (liquid expressions in paths,
 * {@code .liquid} file contents, an optional spawn.json) by executing the
 * {@code @kinotic-ai/spawn} engine in an embedded GraalJS context, so the
 * server renders with exactly the same engine the Kinotic CLI uses.
 */
@Slf4j
@Component
public class GraalJsSpawnRenderer {

    private static final String BUNDLE_RESOURCE = "/spawn/graal-renderer.js";

    private final Object initLock = new Object();
    private volatile Engine engine;
    private volatile Source bundleSource;

    /**
     * Renders {@code files} with {@code context}, returning the rendered files
     * keyed by destination path along with each file's originating template
     * path. Files named spawn.json are consumed as configuration and excluded
     * from the result. This call is CPU-bound and blocking; invoke it from a
     * worker thread, never an event loop.
     *
     * @throws IllegalStateException when the spawn requires a property missing
     *         from {@code context} or the render fails
     */
    public SpawnRenderResult render(Map<String, String> files, Map<String, Object> context) {
        ensureInitialized();

        JsonObject filesJson = new JsonObject();
        files.forEach(filesJson::put);
        String inputJson = new JsonObject()
                .put("files", filesJson)
                .put("context", new JsonObject(context))
                .encode();

        // Contexts are cheap but not thread-safe, so each render gets its own;
        // the shared Engine caches the parsed bundle across contexts.
        try (Context jsContext = Context.newBuilder("js").engine(engine).build()) {
            jsContext.eval(bundleSource);
            Value renderFn = jsContext.getBindings("js")
                                      .getMember("KinoticSpawn")
                                      .getMember("renderSpawn");

            Value promise = renderFn.execute(inputJson);

            AtomicReference<String> rendered = new AtomicReference<>();
            AtomicReference<String> failure = new AtomicReference<>();
            promise.invokeMember("then",
                                 (ProxyExecutable) args -> {
                                     rendered.set(args[0].asString());
                                     return null;
                                 },
                                 (ProxyExecutable) args -> {
                                     failure.set(args[0].toString());
                                     return null;
                                 });

            // GraalJS drains the promise job queue whenever control returns to the
            // host, and the engine performs no real async work, so the promise is
            // settled by the time invokeMember returns.
            if (failure.get() != null) {
                throw new IllegalStateException("Spawn render failed: " + failure.get());
            }
            if (rendered.get() == null) {
                throw new IllegalStateException("Spawn render did not complete synchronously");
            }

            JsonObject result = new JsonObject(rendered.get());
            return new SpawnRenderResult(toStringMap(result.getJsonObject("files")),
                                         toStringMap(result.getJsonObject("sources")));
        }
    }

    private static Map<String, String> toStringMap(JsonObject json) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : json) {
            map.put(entry.getKey(), (String) entry.getValue());
        }
        return map;
    }

    // Initialized lazily rather than at construction: the bundle is extracted from
    // the @kinotic-ai/spawn npm tarball by the extractSpawnRenderer build task and
    // may be absent in partial builds, which must still start a Spring context.
    private void ensureInitialized() {
        if (bundleSource != null) {
            return;
        }
        synchronized (initLock) {
            if (bundleSource != null) {
                return;
            }
            try (InputStream in = GraalJsSpawnRenderer.class.getResourceAsStream(BUNDLE_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException(
                            "Spawn renderer bundle " + BUNDLE_RESOURCE + " is missing from the classpath. "
                            + "It is extracted from the @kinotic-ai/spawn npm package by the "
                            + "kinotic-github extractSpawnRenderer task.");
                }
                String bundle = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                engine = Engine.newBuilder("js")
                               .option("engine.WarnInterpreterOnly", "false")
                               .build();
                bundleSource = Source.newBuilder("js", bundle, "spawn-renderer.js").build();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load spawn renderer bundle " + BUNDLE_RESOURCE, e);
            }
        }
    }

}
