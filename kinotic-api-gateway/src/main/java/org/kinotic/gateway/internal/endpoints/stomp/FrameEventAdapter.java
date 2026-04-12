

package org.kinotic.gateway.internal.endpoints.stomp;

import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.internal.api.event.MapMetadataAdapter;
import io.vertx.ext.stomp.lite.frame.Frame;

/**
 * Adapts a {@link Frame} to a {@link Event}
 *
 *
 * Created by navid on 11/21/19
 */
public class FrameEventAdapter implements Event<byte[]> {

    private final Frame frame;
    private final CRI cri;
    private final Metadata metadata;


    public FrameEventAdapter(Frame frame) {
        this.frame = frame;
        this.cri = CRI.create(frame.getDestination());
        this.metadata = new MapMetadataAdapter(frame.getHeaders());
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
    public byte[] data() {
        return frame.getBody().getBytes();
    }
}
