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

package org.kinotic.rpc.gateway.internal.endpoints.stomp;

import java.util.Map;

import org.kinotic.continuum.core.api.event.CRI;
import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.rpc.gateway.internal.endpoints.EndpointConnectionHandler;
import org.kinotic.rpc.gateway.internal.endpoints.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.stomp.lite.StompServerConnection;
import io.vertx.ext.stomp.lite.StompServerHandler;
import io.vertx.ext.stomp.lite.frame.Frame;
import io.vertx.ext.stomp.lite.frame.InvalidConnectFrame;

/**
 *
 * Created by Navid Mitchell on 2019-02-05.
 */
public class DefaultStompServerHandler implements StompServerHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultStompServerHandler.class);

    private final Vertx vertx;
    private final StompServerConnection connection;
    private final EndpointConnectionHandler endpointConnectionHandler;


    public DefaultStompServerHandler(Vertx vertx,
                                     Services services,
                                     StompServerConnection connection) {
        this.vertx = vertx;
        this.connection = connection;
        this.endpointConnectionHandler = new EndpointConnectionHandler(services);
    }

    @Override
    public Future<Map<String, String>> authenticate(Map<String, String> connectHeaders) {
        return Future.fromCompletionStage(endpointConnectionHandler.authenticate(connectHeaders),
                                                                       vertx.getOrCreateContext());
    }

    @Override
    public void send(Frame frame) {
        // FIXME: this is probably the wrong way to  do this, We are not really providing guaranteed delivery below so this kinda just creates a bottle neck for no reason.
        // We pause the client to effectively make all client requests block until the previous request is handled asynchronously
        connection.pause();

        log.trace("Send Frame received\n{}", frame.toString());

        Event<byte[]> incomingEvent = new FrameEventAdapter(frame);

        endpointConnectionHandler
                .send(incomingEvent)
                .subscribe(null,
                           connection::sendErrorAndDisconnect,
                           () -> {
                               connection.sendReceiptIfNeeded(frame);
                               connection.resume();
                           });
    }

    @Override
    public void subscribe(Frame frame) {
        log.trace("Subscribe Frame received\n{}", frame.toString());

        try {

            String subscriptionId = frame.getHeader(Frame.ID);
            Assert.hasText(subscriptionId,"Subscription requests must contain an Id header");

            CRI cri = CRI.create(frame.getDestination());

            StompSubscriptionEventSubscriber subscriber = new StompSubscriptionEventSubscriber(cri.raw(), subscriptionId, connection);
            endpointConnectionHandler.subscribe(cri, subscriptionId, subscriber);

        } catch (Exception e) {
            log.error("Exception occurred handling subscribe", e);
            connection.sendErrorAndDisconnect(e);
        }
    }

    @Override
    public void unsubscribe(Frame frame) {
        log.trace("Unsubscribe Frame received\n{}", frame.toString());

        try {
            String subscriptionId = frame.getHeader(Frame.ID);

            endpointConnectionHandler.unsubscribe(subscriptionId);

        } catch (Exception e) {
            log.error("Exception occurred handling unsubscribe", e);
            connection.sendErrorAndDisconnect(e);
        }
    }

    @Override
    public void begin(Frame frame) {
        log.debug("Frame received\n" + frame.toString());
    }

    @Override
    public void abort(Frame frame) {
        log.debug("Frame received\n" + frame.toString());
    }

    @Override
    public void commit(Frame frame) {
        log.debug("Frame received\n" + frame.toString());
    }

    @Override
    public void ack(Frame frame) {
        log.debug("Frame received\n" + frame.toString());
    }

    @Override
    public void nack(Frame frame) {
        log.debug("Frame received\n" + frame.toString());
    }

    @Override
    public void exception(Throwable t) {
        // TODO: Add support for auto blacklisting client
        if(t instanceof InvalidConnectFrame){
            log.error("Invalid connect frame "+t.getMessage()+"\nFrame: "+((InvalidConnectFrame)t).getData().toString());
        }else{
            log.error("Client Caused Exception", t);
        }
    }

    @Override
    public void disconnected() {
        // we remove the session since the client disconnected on purpose
        endpointConnectionHandler.removeSession();
    }

    @Override
    public void closed() {
        // We don't remove the session if disconnect was not called because this could be a network issue
        endpointConnectionHandler.shutdown();
    }

}
