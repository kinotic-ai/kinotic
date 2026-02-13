

package org.kinotic.rpc.api.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 *
 * Created by Navid Mitchell on 6/4/20
 */
public class StreamData<I,T> {

    @JsonProperty
    private final StreamOperation streamOperation;
    @Getter
    @JsonProperty
    private final I id;
    @JsonProperty
    private final T value;

    public StreamData(StreamOperation streamOperation, I id, T value) {
        Validate.notNull(streamOperation, "streamOperation must not be null");
        Validate.notNull(id, "id must not be null");
        this.streamOperation = streamOperation;
        this.id = id;
        this.value = value;
    }

    public StreamOperation streamOperation() {
        return streamOperation;
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
                .append("id", id)
                .append("value", value)
                .toString();
    }

}
