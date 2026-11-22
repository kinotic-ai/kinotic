package org.kinotic.testserver;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.PoolOptions;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.Metadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Serves {@code srv://management-api~org.kinotic.testserver.WorkerBench} by forwarding every invocation to the
 * Cloudflare Worker at {@code kinotic.testServer.workerUrl}, the way the gateway's HTTP consumer serves a
 * microservice deployed to Workers: the event's body and headers go to the Worker unchanged, and the Worker's
 * response is the reply.
 */
@Slf4j
@Component
@ConditionalOnProperty("kinotic.testServer.workerUrl")
public class WorkerForwardingConsumer {

    private static final CRI SERVICE_CRI = CRI.create("srv://management-api~org.kinotic.testserver.WorkerBench");
    private static final List<String> FORWARDED_HEADERS = List.of(EventConstants.CONTENT_TYPE_HEADER,
                                                                  EventConstants.TRACEPARENT_HEADER,
                                                                  EventConstants.TRACESTATE_HEADER);

    private final EventBusService eventBusService;
    private final WebClient webClient;
    private final String workerUrl;
    private EventConsumer consumer;

    public WorkerForwardingConsumer(EventBusService eventBusService,
                                    Vertx vertx,
                                    @Value("${kinotic.testServer.workerUrl}") String workerUrl) {
        this.eventBusService = eventBusService;
        // connections to the Worker are kept alive and pooled, as a gateway forwarding steady traffic would keep them
        this.webClient = WebClient.create(vertx,
                                          new WebClientOptions().setKeepAlive(true).setUserAgent("kinotic-test-server"),
                                          new PoolOptions().setHttp1MaxSize(256));
        this.workerUrl = workerUrl.endsWith("/") ? workerUrl.substring(0, workerUrl.length() - 1) : workerUrl;
    }

    @PostConstruct
    void start() {
        consumer = eventBusService.listen(SERVICE_CRI);
        consumer.handler(this::forward);
        log.info("Forwarding {} to {}", SERVICE_CRI.raw(), workerUrl);
    }

    @PreDestroy
    void stop() {
        consumer.unregister();
        webClient.close();
    }

    private void forward(Event<byte[]> event) {
        String function = event.cri().path() != null ? event.cri().path().replaceFirst("^/", "") : "";
        var request = webClient.requestAbs(HttpMethod.POST, workerUrl + "/srv/" + function);
        for (String header : FORWARDED_HEADERS) {
            String value = event.metadata().get(header);
            if (value != null) {
                request.putHeader(header, value);
            }
        }
        request.sendBuffer(Buffer.buffer(event.data() != null ? event.data() : new byte[0]))
               .onSuccess(response -> reply(event, response))
               .onFailure(throwable -> replyError(event, "The Worker could not be reached: " + throwable.getMessage()));
    }

    private void reply(Event<byte[]> request, HttpResponse<Buffer> response) {
        if (response.statusCode() / 100 != 2) {
            replyError(request, "The Worker answered " + response.statusCode() + ": " + response.bodyAsString());
        } else {
            Metadata metadata = replyMetadata(request);
            String contentType = response.getHeader(EventConstants.CONTENT_TYPE_HEADER);
            metadata.put(EventConstants.CONTENT_TYPE_HEADER, contentType != null ? contentType : EventConstants.CONTENT_TYPE_JSON);
            Buffer body = response.body();
            eventBusService.send(Event.create(replyCri(request), metadata, body != null ? body.getBytes() : new byte[0]));
        }
    }

    private void replyError(Event<byte[]> request, String message) {
        Metadata metadata = replyMetadata(request);
        metadata.put(EventConstants.ERROR_HEADER, message);
        eventBusService.send(Event.create(replyCri(request), metadata, new byte[0]));
    }

    // a reply carries every "__" header of its request, which is how the caller correlates it
    private static Metadata replyMetadata(Event<byte[]> request) {
        Metadata ret = Metadata.create();
        for (Map.Entry<String, String> entry : request.metadata()) {
            if (entry.getKey().startsWith("__")) {
                ret.put(entry.getKey(), entry.getValue());
            }
        }
        return ret;
    }

    private static CRI replyCri(Event<byte[]> request) {
        return CRI.create(request.metadata().get(EventConstants.REPLY_TO_HEADER));
    }
}
