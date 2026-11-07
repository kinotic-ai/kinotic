package org.kinotic.client.api;

import io.vertx.core.Future;

/**
 * Opens connections to Kinotic gateways. A Spring Boot application injects it from the client's auto-configuration
 * and opens a {@link KinoticConnection} per gateway it calls.
 *
 * Created by Navíd Mitchell 🤪 on 11/7/26.
 */
public interface KinoticConnectionFactory {

    /**
     * Connects to the gateway the options name, retrying per the options' {@link ReconnectOptions}. The connection
     * runs on a context of its own on the application's Vert.x instance, and keeps a copy of the options, so a
     * change to them after this call does not reach it.
     *
     * @param options where to connect and how to authenticate
     * @return a future completed with the connection once the gateway has accepted it, or failed with an
     *         {@link org.kinotic.core.api.exceptions.AuthenticationException} when the gateway rejects the
     *         credentials, with the rejection when it answers the WebSocket upgrade with another 4xx status, or
     *         with the last failure once the attempts the options allow are spent
     * @throws IllegalArgumentException when the options name no {@code ws://} or {@code wss://} URL of a host without
     *                                  a query or fragment, carry a header name or value HTTP refuses, or bound a
     *                                  size, a delay or a timeout below its least value
     */
    Future<KinoticConnection> connect(ConnectOptions options);

}
