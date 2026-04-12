

package org.kinotic.gateway.internal.endpoints.stomp;

import org.kinotic.core.api.event.Event;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.stomp.lite.frame.Frame;
import io.vertx.ext.stomp.lite.frame.FrameParser;
import io.vertx.ext.stomp.lite.frame.HeaderCodec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 *
 * Created by navid on 9/27/19
 */
public class GatewayUtils {

    public static Frame eventToStompFrame(Event<byte[]> event){
        Map<String, String> headers;
        // Stomp spec says that if there are duplicate headers that the later headers overwrite the previous ones
        // We do this to enforce that spec in the case that the metadata is backed by a multimap
        if(event.metadata() != null){
            headers = new LinkedHashMap<>(event.metadata().size() + 4);
            for (Map.Entry<String,String> entry: event.metadata()) {
                headers.put(entry.getKey(), entry.getValue());
            }
        }else{
            headers = new LinkedHashMap<>( 4);
        }

        // supply message id if none provided
        headers.putIfAbsent(Frame.MESSAGE_ID, UUID.randomUUID().toString());

        // Make sure that internal headers are set properly now
        headers.put(Frame.DESTINATION, event.cri().raw());

        return new Frame(Frame.Command.MESSAGE, headers, event.data() == null ? null : Buffer.buffer(event.data()));
    }


    public static Buffer toStompBuffer(Event<byte[]> event){
        Buffer buffer = Buffer.buffer();
        for (Map.Entry<String, String> entry : event.metadata()) {
            String key = entry.getKey();
            // exclude headers that will be written to the queue as fields
            if(!key.equals(Frame.DESTINATION)
                    && !key.equals(Frame.RECEIPT)) {
                buffer.appendString(HeaderCodec.encode(key, false)
                                            + ":"
                                            + HeaderCodec.encode(entry.getValue(), false)
                                            + "\n");
            }
        }
        buffer.appendString("\n");
        if (event.data() != null) {
            buffer.appendBytes(event.data());
        }
        buffer.appendString(FrameParser.NULL);
        return buffer;
    }

    public static Buffer toStompBuffer(Frame frame){
        Buffer buffer = Buffer.buffer();
        for (Map.Entry<String, String> entry : frame.getHeaders().entrySet()) {
            String key = entry.getKey();
            // exclude headers that will be written to the queue as fields
            if(!key.equals(Frame.DESTINATION)
             && !key.equals(Frame.RECEIPT)) {
                buffer.appendString(HeaderCodec.encode(key, false)
                                    + ":"
                                    + HeaderCodec.encode(entry.getValue(), false)
                                    + "\n");
            }
        }
        buffer.appendString("\n");
        if (frame.getBody() != null) {
            buffer.appendBuffer(frame.getBody());
        }
        buffer.appendString(FrameParser.NULL);
        return buffer;
    }

}
