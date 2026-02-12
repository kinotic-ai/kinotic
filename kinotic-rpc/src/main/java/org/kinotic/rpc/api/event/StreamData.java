

package org.kinotic.rpc.api.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kinotic.rpc.api.crud.Identifiable;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 *
 * Created by Navid Mitchell on 6/4/20
 */
public class StreamData<I,T> implements Identifiable<I> {

    @JsonProperty
    private final StreamOperation streamOperation;
    @JsonProperty
    private final I identity;
    @JsonProperty
    private final T value;

    public StreamData(StreamOperation streamOperation, I identity, T value) {
        Validate.notNull(streamOperation, "streamOperation must not be null");
        Validate.notNull(identity, "identity must not be null");
        this.streamOperation = streamOperation;
        this.identity = identity;
        this.value = value;
    }

    public StreamOperation streamOperation() {
        return streamOperation;
    }

    public I getId() {
        return identity;
    }

    public T value() {
        return value;
    }

    @JsonIgnore
    public boolean isSet(){
        return value() != null;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("streamOperation", streamOperation)
                .append("identity", identity)
                .append("value", value)
                .toString();
    }

}
