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

package org.kinotic.continuum.gateway.internal.endpoints.stomp;

import org.kinotic.continuum.core.api.event.Event;

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

    // FIXME: move to HFT specific code
//    public static void writeHftRawEvent(HftRawEvent event,
//                                        DocumentContext documentContext){
//        // We write the destination to the wire directly so not all bytes have to be parsed
//        documentContext.wire()
//                       .bytes()
//                       .writeUtf8(event.cri()) // So we know where the data was sent
//                       .writeByte(event.dataFormat()) // raw data format
//                       .writeInt(event.data().length)// length of the rest of the data
//                       .write(event.data()); // rest of the data
//    }
//
//    public static HftRawEvent readHftRawEvent(DocumentContext documentContext){
//        Bytes<?> hftBytes = documentContext.wire().bytes();
//        String cri = hftBytes.readUtf8();
//        byte dataFormat = hftBytes.readByte();
//        int length = hftBytes.readInt();
//
//        // now read raw data
//        byte[] dataBytes = {};
//        if(length > 0) {
//            dataBytes = new byte[length];
//            hftBytes.read(dataBytes);
//        }
//        return new HftRawEvent(cri, dataFormat, dataBytes);
//    }
//
//    public static HftRawEvent continuumEventToHftRawEvent(Event<byte[]> event){
//        String rawCri = event.cri().raw();
//        return new HftRawEvent(rawCri, EventConstants.RAW_EVENT_FORMAT_STOMPISH, toStompBuffer(event).getBytes());
//    }
//
//    public static HftRawEvent stompFrameToHftRawEvent(Frame frame){
//        String rawCri = frame.getDestination();
//        return new HftRawEvent(rawCri, EventConstants.RAW_EVENT_FORMAT_STOMPISH, toStompBuffer(frame).getBytes());
//    }

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
