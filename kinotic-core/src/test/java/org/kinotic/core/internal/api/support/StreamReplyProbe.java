package org.kinotic.core.internal.api.support;

import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.internal.utils.EventUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Requests a stream over raw events and records what arrives on its reply destination: the integer values in
 * order, whether it completed, and any error, so a test can drive the stream with control events directly.
 */
public class StreamReplyProbe implements AutoCloseable {

    private final EventBusService eventBusService;
    private final CRI streamCri;
    private final String correlationId = UUID.randomUUID().toString();
    private final List<Integer> values = Collections.synchronizedList(new ArrayList<>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean completed = new AtomicBoolean();
    private final EventConsumer replyConsumer;
    private final CRI replyCri;

    public StreamReplyProbe(EventBusService eventBusService, CRI serviceCri, String method) throws Exception {
        this.eventBusService = eventBusService;
        this.streamCri = CRI.create(serviceCri.scheme(), serviceCri.scope(), serviceCri.resourceName(), "/" + method, serviceCri.version());
        this.replyCri = CRI.create(EventConstants.REPLY_DESTINATION_SCHEME, UUID.randomUUID().toString(), "org.kinotic.tests.StreamReplyProbe");
        this.replyConsumer = eventBusService.listen(replyCri);
        replyConsumer.handler(reply -> {
            if (reply.metadata().contains(EventConstants.ERROR_HEADER)) {
                errors.add(reply.metadata().get(EventConstants.ERROR_HEADER));
            } else if (EventConstants.CONTROL_VALUE_COMPLETE.equals(reply.metadata().get(EventConstants.CONTROL_HEADER))) {
                completed.set(true);
            } else {
                values.add(Integer.parseInt(new String(reply.data(), StandardCharsets.UTF_8)));
            }
        });
        replyConsumer.completion().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /**
     * Sends the request that opens the stream, with its positional arguments as a JSON array.
     */
    public void request(String argumentsJson) {
        Metadata metadata = Metadata.create();
        metadata.put(EventConstants.REPLY_TO_HEADER, replyCri.raw());
        metadata.put(EventConstants.CORRELATION_ID_HEADER, correlationId);
        metadata.put(EventConstants.CONTENT_TYPE_HEADER, "application/json");
        eventBusService.send(Event.create(streamCri, metadata, argumentsJson.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Publishes a control event for the stream, such as a suspend, to its service.
     */
    public void control(String control) {
        eventBusService.publish(EventUtil.createStreamControlEvent(streamCri, correlationId, control));
    }

    public List<Integer> values() {
        synchronized (values) {
            return List.copyOf(values);
        }
    }

    public int valueCount() {
        return values.size();
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public boolean completed() {
        return completed.get();
    }

    @Override
    public void close() {
        replyConsumer.unregister();
    }

}
