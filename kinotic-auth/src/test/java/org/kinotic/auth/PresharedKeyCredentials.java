package org.kinotic.auth;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;

import java.util.concurrent.Executor;

/**
 * gRPC call credentials presenting a SpiceDB preshared key as a bearer token.
 */
final class PresharedKeyCredentials extends CallCredentials {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final String token;

    PresharedKeyCredentials(String token) {
        this.token = token;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier applier) {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, "Bearer " + token);
        try {
            applier.apply(headers);
        } catch (RuntimeException e) {
            applier.fail(Status.UNAUTHENTICATED.withCause(e));
        }
    }
}
