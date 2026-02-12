

package org.kinotic.rpc.api.event;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * Created by navid on 11/6/19
 */
class DefaultMetadata implements Metadata{

    private final Map<String,String> delegate;

    public DefaultMetadata() {
        delegate = new LinkedHashMap<>();
    }

    public DefaultMetadata(Map<String, String> delegate) {
        this.delegate = new LinkedHashMap<>(delegate);
    }

    @Override
    public boolean contains(String key) {
        return delegate.containsKey(key);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return delegate.entrySet().iterator();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public String get(String key) {
        return delegate.get(key);
    }

    @Override
    public void put(String key, String value) {
        delegate.put(key, value);
    }

    public void remove(String key) {
        delegate.remove(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

}
