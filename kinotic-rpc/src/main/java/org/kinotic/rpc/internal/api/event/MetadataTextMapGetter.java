package org.kinotic.rpc.internal.api.event;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.kinotic.rpc.api.event.Metadata;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by Navíd Mitchell 🤪 on 10/9/24.
 */
public class MetadataTextMapGetter implements TextMapGetter<Metadata> {
    @Override
    public Iterable<String> keys(Metadata carrier) {
        ArrayList<String> keys = new ArrayList<>(carrier.size());
        for(Map.Entry<String, String> entry : carrier){
            keys.add(entry.getKey());
        }
        return keys;
    }

    @Override
    public String get(Metadata carrier, String key) {
        return carrier == null ? null : carrier.get(key);
    }
}
