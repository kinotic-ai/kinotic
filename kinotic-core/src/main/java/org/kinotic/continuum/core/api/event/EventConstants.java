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

package org.kinotic.continuum.core.api.event;

import org.kinotic.continuum.api.security.ConnectedInfo;

/**
 *
 * Created by Navid Mitchell on 2018-12-11.
 */
public class EventConstants {

    public static final String CRI_HEADER = "cri";

    public static final String SENDER_HEADER = "sender";

    public static final String CONTENT_TYPE_HEADER = "content-type";

    public static final String CONTENT_LENGTH_HEADER = "content-length";

    public static final String REPLY_TO_HEADER = "reply-to";

    /**
     * This is the replyToId that was supplied to the client
     */
    public static final String REPLY_TO_ID_HEADER = "reply-to-id";


    /**
     * Header provided by the sever on connection to represent the users session id
     */
    public static final String SESSION_HEADER = "session";

    /**
     * Header provided by the client on connection request to represent that the server
     * should not keep the session alive after any network disconnection.
     */
    public static final String DISABLE_STICKY_SESSION_HEADER = "disable-sticky-session";

    /**
     * Header provided by the server on connection to provide the {@link ConnectedInfo}
     */
    public static final String CONNECTED_INFO_HEADER = "connected-info";

    /**
     * Correlates a response with a given request
     * Headers that start with __ will always be persisted between messages
     */
    public static final String CORRELATION_ID_HEADER = "__correlation-id";

    /**
     * Denotes that something caused an error. Will contain a brief message about the error.
     */
    public static final String ERROR_HEADER = "error";

    /**
     * Denotes the event is a control plane event. These are used for internal coordination.
     */
    public static final String CONTROL_HEADER = "control";

    /**
     * Stream is complete, no further values will be sent.
     */
    public static final String CONTROL_VALUE_COMPLETE = "complete";

    public static final String CONTROL_VALUE_CANCEL = "cancel";

    public static final String CONTROL_VALUE_SUSPEND = "suspend";

    public static final String CONTROL_VALUE_RESUME = "resume";


    public static final String SERVICE_DESTINATION_SCHEME = "srv";

    public static final String STREAM_DESTINATION_SCHEME = "stream";

    /**
     * Event data format that is pretty much a stomp frame.
     * The difference being the Destination is in place of the COMMAND portion. And there is no Destination header. Everything else is the same.
     */
    public static final byte RAW_EVENT_FORMAT_STOMPISH = 0x01;

    /**
     * Event data format that can be used for raw UTF-8 data
     */
    public static final byte RAW_EVENT_FORMAT_UTF8 = 0x02;

    /**
     * The traceparent HTTP header field identifies the incoming request in a tracing system. It has four fields:
     *
     *     version
     *     trace-id
     *     parent-id
     *     trace-flags
     * @see <a href="https://www.w3.org/TR/trace-context/#traceparent-header">Traceparent Header Docs</a>
     */
    public static final String TRACEPARENT_HEADER = "traceparent";

    /**
     * The main purpose of the tracestate header is to provide additional vendor-specific trace identification information across different distributed tracing systems and is a companion header for the traceparent field. It also conveys information about the request’s position in multiple distributed tracing graphs.
     * @see <a href="https://www.w3.org/TR/trace-context/#tracestate-header">Tracestate Header Docs</a>
     */
    public static final String TRACESTATE_HEADER = "tracestate";

}
