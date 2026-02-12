

package org.kinotic.rpc.api.event;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 *
 * Created by navid on 11/6/19
 */
public class DefaultEvent<T> implements Event<T>{

    private final CRI cri;
    private final Metadata metadata;
    private final T data;

    public DefaultEvent(CRI cri, Metadata metadata, T data) {
        Validate.notNull(cri, "CRI must not be null");
        Validate.notNull(cri, "metadata must not be null");
        this.cri = cri;
        this.metadata = metadata;
        this.data = data;
    }

    public DefaultEvent(CRI cri, T data) {
        this(cri, new DefaultMetadata(), data);
    }

    @Override
    public CRI cri() {
        return cri;
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public T data() {
        return data;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("cri", cri)
                .append("metadata", metadata)
                .append("data", data)
                .toString();
    }
}
