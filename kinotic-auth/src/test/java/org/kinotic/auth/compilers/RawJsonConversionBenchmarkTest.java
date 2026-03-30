package org.kinotic.auth.compilers;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Benchmark for raw JSON to protobuf Value conversion.
 * Measures the cost of the single parse step the gateway would perform.
 */
class RawJsonConversionBenchmarkTest {

    private static Value rawJsonToProtobufValue(String rawJson) throws Exception {
        String wrappedJson = "{\"args\": " + rawJson + "}";
        Struct.Builder structBuilder = Struct.newBuilder();
        JsonFormat.parser().merge(wrappedJson, structBuilder);
        return structBuilder.build().getFieldsOrThrow("args");
    }

    /**
     * Generates a large JSON array of N objects, each with several fields
     * including nested objects and arrays.
     */
    private static String generateLargePayload(int numArgs, int fieldsPerArg) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < numArgs; i++) {
            if (i > 0) sb.append(",");
            sb.append("{");
            for (int f = 0; f < fieldsPerArg; f++) {
                if (f > 0) sb.append(",");
                switch (f % 5) {
                    case 0 -> sb.append("\"string_field_").append(f).append("\": \"value_").append(i).append("_").append(f).append("\"");
                    case 1 -> sb.append("\"number_field_").append(f).append("\": ").append(i * 1000 + f);
                    case 2 -> sb.append("\"bool_field_").append(f).append("\": ").append(i % 2 == 0);
                    case 3 -> sb.append("\"nested_field_").append(f).append("\": {\"inner\": \"val_").append(f).append("\", \"count\": ").append(f).append("}");
                    case 4 -> sb.append("\"array_field_").append(f).append("\": [\"a\", \"b\", \"c\", ").append(f).append("]");
                }
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    // ========== Array-to-Named-Object Conversion ==========

    /**
     * Simulates what the gateway would do if we switched to named args for Cedar:
     * take the raw JSON array and wrap each element with its parameter name.
     * Uses Jackson streaming to read the array and write a new object without
     * building an intermediate tree.
     *
     * @param rawJsonArray the raw JSON array payload, e.g. [{"amount":25000},{"approved":true}]
     * @param paramNames   the method parameter names in order, e.g. ["order", "approval"]
     * @return a JSON object string, e.g. {"order":{"amount":25000},"approval":{"approved":true}}
     */
    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();

    private static String arrayToNamedObject(String rawJsonArray, String[] paramNames) throws Exception {
        var writer = new java.io.StringWriter();
        try (var reader = MAPPER.createParser(rawJsonArray);
             var gen = MAPPER.createGenerator(writer)) {

            gen.writeStartObject();
            reader.nextToken(); // START_ARRAY
            int i = 0;
            while (reader.nextToken() != tools.jackson.core.JsonToken.END_ARRAY) {
                if (i >= paramNames.length) {
                    throw new IllegalArgumentException("More args than parameter names");
                }
                gen.writeFieldName(paramNames[i]);
                gen.copyCurrentStructure(reader);
                i++;
            }
            gen.writeEndObject();
        }
        return writer.toString();
    }

    @Test
    void benchmarkArrayToNamedObject_Small() throws Exception {
        String payload = "[{\"amount\": 25000, \"department\": \"sales\", \"currency\": \"USD\"}]";
        String[] names = {"order"};
        runNamedObjectBenchmark("Small (1 arg, 3 fields)", payload, names, 10000);
    }

    @Test
    void benchmarkArrayToNamedObject_Medium() throws Exception {
        String payload = generateLargePayload(3, 10);
        String[] names = {"arg0", "arg1", "arg2"};
        runNamedObjectBenchmark("Medium (3 args, 10 fields each)", payload, names, 10000);
    }

    @Test
    void benchmarkArrayToNamedObject_Large() throws Exception {
        String payload = generateLargePayload(10, 20);
        String[] names = {"a0","a1","a2","a3","a4","a5","a6","a7","a8","a9"};
        runNamedObjectBenchmark("Large (10 args, 20 fields each)", payload, names, 5000);
    }

    @Test
    void benchmarkArrayToNamedObject_VeryLarge() throws Exception {
        String payload = generateLargePayload(50, 50);
        String[] names = java.util.stream.IntStream.range(0, 50).mapToObj(n -> "a" + n).toArray(String[]::new);
        runNamedObjectBenchmark("Very Large (50 args, 50 fields each)", payload, names, 1000);
    }

    @Test
    void benchmarkArrayToNamedObject_Correctness() throws Exception {
        String payload = "[{\"amount\": 25000}, {\"approved\": true}]";
        String[] names = {"order", "approval"};
        String result = arrayToNamedObject(payload, names);
        System.out.println("Conversion result: " + result);
        assertTrue(result.contains("\"order\""));
        assertTrue(result.contains("\"approval\""));
        assertTrue(result.contains("\"amount\":25000") || result.contains("\"amount\": 25000"));
        assertTrue(result.contains("\"approved\":true") || result.contains("\"approved\": true"));
    }

    private void runNamedObjectBenchmark(String label, String payload, String[] paramNames, int iterations) throws Exception {
        int payloadSize = payload.length();

        // Warmup
        for (int i = 0; i < 1000; i++) {
            arrayToNamedObject(payload, paramNames);
        }

        // Timed run
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            arrayToNamedObject(payload, paramNames);
        }
        long elapsed = System.nanoTime() - start;

        double avgMicros = (elapsed / (double) iterations) / 1000.0;
        double avgMillis = avgMicros / 1000.0;
        String result = arrayToNamedObject(payload, paramNames);

        System.out.printf("""
                === Array→NamedObject: %s ===
                Input size:  %,d bytes
                Output size: %,d bytes
                Iterations: %,d
                Avg per call: %.2f µs (%.3f ms)
                Throughput: %,.0f calls/sec
                %n""",
                label,
                payloadSize,
                result.length(),
                iterations,
                avgMicros,
                avgMillis,
                1_000_000.0 / avgMicros);
    }

    // ========== Protobuf Conversion Benchmarks ==========

    @Test
    void benchmarkSmallPayload() throws Exception {
        // Typical service call: 1-2 args with a few fields
        String payload = "[{\"amount\": 25000, \"department\": \"sales\", \"currency\": \"USD\"}]";
        runBenchmark("Small (1 arg, 3 fields)", payload, 10000);
    }

    @Test
    void benchmarkMediumPayload() throws Exception {
        // Moderate: 3 args with 10 fields each
        String payload = generateLargePayload(3, 10);
        runBenchmark("Medium (3 args, 10 fields each)", payload, 10000);
    }

    @Test
    void benchmarkLargePayload() throws Exception {
        // Large: 10 args with 20 fields each (including nested objects and arrays)
        String payload = generateLargePayload(10, 20);
        runBenchmark("Large (10 args, 20 fields each)", payload, 5000);
    }

    @Test
    void benchmarkVeryLargePayload() throws Exception {
        // Stress test: 50 args with 50 fields each
        String payload = generateLargePayload(50, 50);
        runBenchmark("Very Large (50 args, 50 fields each)", payload, 1000);
    }

    @Test
    void benchmarkHugePayload() throws Exception {
        // Extreme: single massive object
        String payload = generateLargePayload(1, 500);
        runBenchmark("Huge (1 arg, 500 fields)", payload, 1000);
    }

    private void runBenchmark(String label, String payload, int iterations) throws Exception {
        int payloadSize = payload.length();

        // Warmup
        for (int i = 0; i < 1000; i++) {
            rawJsonToProtobufValue(payload);
        }

        // Timed run
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            rawJsonToProtobufValue(payload);
        }
        long elapsed = System.nanoTime() - start;

        double avgMicros = (elapsed / (double) iterations) / 1000.0;
        double avgMillis = avgMicros / 1000.0;

        System.out.printf("""
                === %s ===
                Payload size: %,d bytes
                Iterations: %,d
                Avg per call: %.2f µs (%.3f ms)
                Throughput: %,.0f calls/sec
                %n""",
                label,
                payloadSize,
                iterations,
                avgMicros,
                avgMillis,
                1_000_000.0 / avgMicros);
    }
}
