package org.kinotic.client.internal.api;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.client.api.ConnectOptions;
import org.kinotic.client.api.KinoticConnection;
import org.kinotic.client.api.KinoticConnectionFactory;
import org.kinotic.core.api.RpcServiceProxyFactory;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.Participant;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default {@link KinoticConnectionFactory}: every connection runs on the application's Vert.x instance, mints its
 * proxies through the application's {@link RpcServiceProxyFactory} and reads the gateway's connected info with the
 * application's Jackson mapper.
 *
 * Created by Navíd Mitchell 🤪 on 11/7/26.
 */
public class DefaultKinoticConnectionFactory implements KinoticConnectionFactory {

    private static final Set<String> WEBSOCKET_SCHEMES = Set.of("ws", "wss");

    private final Vertx vertx;
    private final RpcServiceProxyFactory proxyFactory;
    private final Validator validator;
    private final ObjectReader connectedInfoReader;

    public DefaultKinoticConnectionFactory(Vertx vertx, RpcServiceProxyFactory proxyFactory, Validator validator, JsonMapper jsonMapper) {
        this.vertx = vertx;
        this.proxyFactory = proxyFactory;
        this.validator = validator;
        // the gateway's participant model is not on this classpath, so the participant of the connected info is read
        // as what every participant has, and the properties its own model adds are skipped whatever the
        // application's mapper does with an unknown property
        this.connectedInfoReader = jsonMapper.rebuild()
                                             .addModule(new SimpleModule("KinoticClientModule").addAbstractTypeMapping(Participant.class, ConnectedParticipant.class))
                                             .build()
                                             .readerFor(ConnectedInfo.class)
                                             .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public Future<KinoticConnection> connect(ConnectOptions options) {
        Validate.notNull(options, "options must not be null");
        // validated as copied, so a change to the caller's options while or after connect() runs reaches neither the
        // validation nor the connection
        ConnectOptions validated = new ConnectOptions(options);
        Set<ConstraintViolation<ConnectOptions>> violations = validator.validate(validated);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                                                         .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                                                         .sorted()
                                                         .collect(Collectors.joining(", ")));
        }
        URI serverUri = URI.create(validated.getServerUrl());
        Validate.isTrue(serverUri.getScheme() != null && WEBSOCKET_SCHEMES.contains(serverUri.getScheme()),
                        "serverUrl must be a ws:// or wss:// URL but was '%s'", validated.getServerUrl());
        Validate.isTrue(serverUri.getHost() != null, "serverUrl must name a host but was '%s'", validated.getServerUrl());
        Validate.isTrue(!StringUtils.stripEnd(serverUri.getPath(), "/").endsWith(EventConstants.STOMP_WEBSOCKET_PATH),
                        "serverUrl names the gateway without its %s path, which the client appends itself",
                        EventConstants.STOMP_WEBSOCKET_PATH);
        Validate.isTrue(serverUri.getQuery() == null && serverUri.getFragment() == null,
                        "serverUrl must carry no query or fragment but was '%s'", validated.getServerUrl());
        // the upgrade request refuses a header name or value outside what HTTP allows, so they are checked here
        // once rather than failing every attempt
        MultiMap headers = HttpHeaders.headers();
        validated.getAuthHeaders().forEach(headers::add);
        // credentials over ws:// leave the process in clear, so a host other than loopback takes them only by choice
        Validate.isTrue(!"ws".equals(serverUri.getScheme()) || validated.getAuthHeaders().isEmpty()
                                || isLoopback(serverUri.getHost()) || validated.isAllowCleartextCredentials(),
                        "authHeaders over ws:// to a host other than localhost need allowCleartextCredentials");
        return new DefaultKinoticConnection(vertx, validated, proxyFactory, connectedInfoReader).start();
    }

    /**
     * @param host the host of a server URL, an IPv6 literal in brackets
     * @return whether it is {@code localhost} or a loopback address literal, such as {@code 127.0.0.1} or {@code [::1]}
     */
    private static boolean isLoopback(String host) {
        boolean ret = "localhost".equalsIgnoreCase(host);
        if (!ret) {
            try {
                ret = InetAddress.ofLiteral(host).isLoopbackAddress();
            } catch (IllegalArgumentException notALiteral) {
                // a hostname, which resolves on the attempt to anywhere, so it is not loopback here
            }
        }
        return ret;
    }

}
