

package org.kinotic.rpc.gateway.internal.hft;

import org.kinotic.rpc.api.event.EventConstants;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Base64;

/**
 * Default data container for data written to the gateway events ChronicleQueue
 *
 * Created by navid on 11/19/19
 */
public class HftRawEvent {

    private final String cri;
    private final byte dataFormat;
    private final byte[] data;
    private static final Base64.Encoder encoder = Base64.getEncoder();


    public HftRawEvent(String cri, byte[] data) {
        this(cri, EventConstants.RAW_EVENT_FORMAT_STOMPISH, data);
    }

    public HftRawEvent(String cri, byte dataFormat, byte[] data) {
        this.cri = cri;
        this.dataFormat = dataFormat;
        this.data = data;
    }

    public String cri() {
        return cri;
    }

    /**
     * Value describing the format that the {@link HftRawEvent#data()} is in
     * @return the byte containing the format
     */
    public byte dataFormat() {
        return dataFormat;
    }

    /**
     * The raw data for this {@link HftRawEvent}
     * @return the bytes with the raw data
     */
    public byte[] data() {
        return data;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof HftRawEvent)) return false;

        HftRawEvent that = (HftRawEvent) o;

        return new EqualsBuilder()
                .append(cri, cri)
                .append(dataFormat, that.dataFormat())
                .append(data, that.data())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 397)
                .append(cri)
                .append(dataFormat)
                .append(data)
                .toHashCode();
    }

    @Override
    public String toString() {
        if(dataFormat == EventConstants.RAW_EVENT_FORMAT_UTF8) {
            return new ToStringBuilder(this)
                    .append("cri", cri)
                    .append("data", new String(data))
                    .toString();
        }else{
            return data != null && data.length > 0 ? encoder.encodeToString(data) : "";
        }
    }
}
