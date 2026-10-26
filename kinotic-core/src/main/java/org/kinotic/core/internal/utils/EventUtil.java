

package org.kinotic.core.internal.utils;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.internal.api.service.invoker.ServiceInvocationSupervisor;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.function.Supplier;

/**
 *
 * Created by navid on 2019-07-24.
 */
public class EventUtil {

    /**
     * Creates a {@link Event} that can be sent based on the incomingEvent headers and the data to use as the body
     * @param incomingMetadata the original {@link Metadata} sent to the {@link ServiceInvocationSupervisor}
     * @param headers key value pairs that will be added to the outgoing headers
     * @param bodySupplier that will provide the bytes needed for the message body.
     *                     A supplier is used so all validations can occur before doing the work of creating the body bytes.
     * @return the {@link Event} to send
     */
    public static Event<byte[]> createReplyEvent(Metadata incomingMetadata, Map<String, String> headers, Supplier<byte[]> bodySupplier){
        Validate.notNull(incomingMetadata, "incomingEvent cannot be null");

        String replyCRI = incomingMetadata.get(EventConstants.REPLY_TO_HEADER);
        Assert.hasText(replyCRI, "No reply-to header found cannot create outgoing message");

        Metadata newMetadata;
        if(headers != null){
            newMetadata = Metadata.create(headers);
        }else{
            newMetadata = Metadata.create();
        }

        // we must persist any headers that begin with __
        for(Map.Entry<String, String> entry: incomingMetadata){
            if(entry.getKey().startsWith("__")) {
                newMetadata.put(entry.getKey(), entry.getValue());
            }
        }

        return Event.create(replyCRI, newMetadata, bodySupplier != null ?  bodySupplier.get() : null);
    }

    /**
     * The metadata an error reply to a request needs: the reply destination and the headers every reply
     * persists, which is everything a hop holding the request outstanding has to keep.
     * @param requestMetadata the request's metadata
     * @return a new {@link Metadata} carrying the reply-to header and every {@code __} header
     */
    public static Metadata replyMetadataOf(Metadata requestMetadata) {
        Metadata ret = Metadata.create();
        for (Map.Entry<String, String> entry : requestMetadata) {
            if (EventConstants.REPLY_TO_HEADER.equals(entry.getKey()) || entry.getKey().startsWith("__")) {
                ret.put(entry.getKey(), entry.getValue());
            }
        }
        return ret;
    }

    /**
     * Creates a control event for a stream, published to the service producing it so the instance holding
     * the stream acts on it and every other instance ignores it.
     * @param serviceCri the stream's origin, the {@link EventConstants#ORIGIN_CRI_HEADER} its values carry
     * @param correlationId the correlation id of the request the stream answers
     * @param control the control value, such as {@link EventConstants#CONTROL_VALUE_CANCEL}
     * @return the control {@link Event}, with no body
     */
    public static Event<byte[]> createStreamControlEvent(CRI serviceCri, String correlationId, String control) {
        Metadata metadata = Metadata.create();
        metadata.put(EventConstants.CONTROL_HEADER, control);
        metadata.put(EventConstants.CORRELATION_ID_HEADER, correlationId);
        return Event.create(serviceCri, metadata, null);
    }

    /**
     * Whether a reply ends the request it answers: an error reply, or one carrying the completion marker,
     * which every single-value reply and every stream completion does.
     * @param replyMetadata the reply's metadata
     * @return true when no further reply follows for the request
     */
    public static boolean isTerminalReply(Metadata replyMetadata) {
        return replyMetadata.contains(EventConstants.ERROR_HEADER)
                || EventConstants.CONTROL_VALUE_COMPLETE.equals(replyMetadata.get(EventConstants.CONTROL_HEADER));
    }

    public static String toString(Event<byte[]> event, boolean includeData) {
        StringBuilder sb = new StringBuilder("Event<byte>{\n");
        sb.append("\tcri=");
        sb.append(event.cri());
        sb.append("\n");
        sb.append("\tmetadata={\n");
        if (event.metadata() != null) {
            for (Map.Entry<String, String> entry : event.metadata()) {
                sb.append("\t\t");
                sb.append(entry.getKey());
                sb.append(": ");
                sb.append(entry.getValue());
                sb.append("\n");
            }
        }
        sb.append("\t}\n");

        if(includeData && event.data() != null && event.data().length > 0){
            sb.append("\tdata=\n\t\t");
            sb.append(new String(event.data()));
        }

        sb.append("\n}");

        return sb.toString();
    }






}
