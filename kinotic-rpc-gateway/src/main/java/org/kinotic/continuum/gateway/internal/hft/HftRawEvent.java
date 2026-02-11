/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.gateway.internal.hft;

import org.kinotic.continuum.core.api.event.EventConstants;
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
