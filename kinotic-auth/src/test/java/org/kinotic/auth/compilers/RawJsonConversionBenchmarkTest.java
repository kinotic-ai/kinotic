package org.kinotic.auth.compilers;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

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
