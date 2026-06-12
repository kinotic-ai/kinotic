package org.kinotic.github.internal.api.services;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraalJsSpawnRendererTest {

    private final GraalJsSpawnRenderer renderer = new GraalJsSpawnRenderer();

    @Test
    void rendersLiquidContentPathsAndGlobals() {
        Map<String, String> files = Map.of(
                "package.json.liquid", "{\"name\": \"{{projectName}}\", \"core\": \"{{ kinoticApiVersion }}\"}",
                "src/{{ projectName | camelCase | upperFirst }}.ts.liquid", "export class {{ projectName | camelCase | upperFirst }} {}",
                ".gitignore", "node_modules\n",
                "spawn.json", "{\"globals\": {\"kinoticApiVersion\": \"^1.0.9\"}}");

        Map<String, String> result = renderer.render(files, Map.of("projectName", "my-app"));

        assertEquals("{\"name\": \"my-app\", \"core\": \"^1.0.9\"}", result.get("package.json"));
        assertEquals("export class MyApp {}", result.get("src/MyApp.ts"));
        assertEquals("node_modules\n", result.get(".gitignore"));
        assertFalse(result.containsKey("spawn.json"));
    }

    @Test
    void contextOverridesGlobals() {
        Map<String, String> files = Map.of(
                "out.txt.liquid", "{{ flavor }}",
                "spawn.json", "{\"globals\": {\"flavor\": \"from-globals\"}}");

        Map<String, String> result = renderer.render(files, Map.of("flavor", "from-context"));

        assertEquals("from-context", result.get("out.txt"));
    }

    @Test
    void failsOnMissingRequiredProperty() {
        Map<String, String> files = Map.of(
                "spawn.json", "{\"propertySchema\": {\"projectName\": {\"type\": \"string\"}}}");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                                                () -> renderer.render(files, Map.of()));
        assertTrue(ex.getMessage().contains("projectName"));
    }

    @Test
    void rendersConcurrently() throws Exception {
        Map<String, String> files = Map.of("out.txt.liquid", "{{ value }}");

        Thread[] threads = new Thread[4];
        String[] results = new String[threads.length];
        for (int i = 0; i < threads.length; i++) {
            final int n = i;
            threads[i] = new Thread(() -> results[n] = renderer.render(files, Map.of("value", "v" + n)).get("out.txt"));
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        for (int i = 0; i < threads.length; i++) {
            assertEquals("v" + i, results[i]);
        }
    }

}
