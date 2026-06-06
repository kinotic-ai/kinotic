

package org.kinotic.core.api.event;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.kinotic.core.api.security.Participant;

/**
 *
 * Created by navid on 11/6/19
 */
public class DefaultEvent<T> implements Event<T>{

    private final CRI cri;
    private final Metadata metadata;
    private final T data;
    private Participant sender;

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

    public DefaultEvent(CRI cri, Metadata metadata, T data, Participant sender) {
        this(cri, metadata, data);
        this.sender = sender;
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
    public Participant sender() {
        return sender;
    }

    @Override
    public void setSender(Participant sender) {
        this.sender = sender;
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
