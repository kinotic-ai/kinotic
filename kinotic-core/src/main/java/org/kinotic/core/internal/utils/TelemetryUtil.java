package org.kinotic.core.internal.utils;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.kinotic.core.api.event.Metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How a Kinotic service invocation is described to OpenTelemetry, shared by the calling and
 * receiving ends so the two halves of one call agree on their names and carrier.
 *
 * Created by Claude on 2026-08-15.
 */
public class TelemetryUtil {

    /**
     * Identifies the RPC system in span attributes. The Kinotic client reports the same value for
     * its half of the call.
     */
    public static final String SYSTEM_VALUE = "kinotic";

    public static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");
    public static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");
    public static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");

    /**
     * Reads the trace context a caller wrote into the event headers.
     */
    public static final TextMapGetter<Metadata> METADATA_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Metadata carrier) {
            List<String> ret = new ArrayList<>(carrier.size());
            for (Map.Entry<String, String> entry : carrier) {
                ret.add(entry.getKey());
            }
            return ret;
        }

        @Override
        public String get(Metadata carrier, String key) {
            return carrier != null ? carrier.get(key) : null;
        }
    };

    /**
     * Writes the trace context into the event headers of an outbound invocation.
     */
    public static final TextMapSetter<Metadata> METADATA_SETTER = (carrier, key, value) -> {
        if (carrier != null) {
            carrier.put(key, value);
        }
    };

}
