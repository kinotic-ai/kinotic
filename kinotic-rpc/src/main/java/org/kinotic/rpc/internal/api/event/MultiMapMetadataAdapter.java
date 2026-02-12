

package org.kinotic.rpc.internal.api.event;

import org.kinotic.rpc.api.event.Metadata;
import io.vertx.core.MultiMap;

import java.util.Iterator;
import java.util.Map;

/**
 * {@link Metadata} implementation backed by a {@link MultiMap}
 *
 * Created by navid on 10/30/19
 */
public class MultiMapMetadataAdapter implements Metadata {

    private final MultiMap multiMap;

    public MultiMapMetadataAdapter(MultiMap multiMap) {
        this.multiMap = multiMap;
    }

    @Override
    public String get(String key) {
        return multiMap.get(key);
    }

    @Override
    public void put(String key, String value) {
        multiMap.set(key, value);
    }

    @Override
    public void remove(String key) {
        multiMap.remove(key);
    }

    @Override
    public boolean contains(String key) {
        return multiMap.contains(key);
    }

    @Override
    public void clear() {
        multiMap.clear();
    }

    @Override
    public boolean isEmpty() {
        return multiMap.isEmpty();
    }

    @Override
    public int size() {
        return multiMap.size();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return multiMap.iterator();
    }

    public MultiMap getMultiMap(){
        return multiMap;
    }

}
