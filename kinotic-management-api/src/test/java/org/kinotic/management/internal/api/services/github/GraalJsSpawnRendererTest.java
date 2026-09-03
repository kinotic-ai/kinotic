package org.kinotic.management.internal.api.services.github;

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

        SpawnRenderResult result = renderer.render(files, Map.of("projectName", "my-app"));

        assertEquals("{\"name\": \"my-app\", \"core\": \"^1.0.9\"}", result.files().get("package.json"));
        assertEquals("export class MyApp {}", result.files().get("src/MyApp.ts"));
        assertEquals("node_modules\n", result.files().get(".gitignore"));
        assertFalse(result.files().containsKey("spawn.json"));
        // sources traces each rendered file back to its template path
        assertEquals("package.json.liquid", result.sources().get("package.json"));
        assertEquals("src/{{ projectName | camelCase | upperFirst }}.ts.liquid",
                     result.sources().get("src/MyApp.ts"));
    }

    @Test
    void contextOverridesGlobals() {
        Map<String, String> files = Map.of(
                "out.txt.liquid", "{{ flavor }}",
                "spawn.json", "{\"globals\": {\"flavor\": \"from-globals\"}}");

        SpawnRenderResult result = renderer.render(files, Map.of("flavor", "from-context"));

        assertEquals("from-context", result.files().get("out.txt"));
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
            threads[i] = new Thread(() -> results[n] = renderer.render(files, Map.of("value", "v" + n)).files().get("out.txt"));
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
