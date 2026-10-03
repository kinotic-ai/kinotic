package org.kinotic.stream.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.stream.api.config.KinoticStreamProperties;
import org.kinotic.stream.api.model.StartPosition;
import org.kinotic.stream.api.model.StreamDefinition;
import org.kinotic.stream.api.model.StreamPosition;
import org.kinotic.stream.api.services.StreamService;
import org.kinotic.stream.api.services.StreamSubscription;
import org.kinotic.stream.internal.log.StreamLog;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DefaultStreamService implements StreamService {

    // Stream and consumer names become directory and file names, so they can never contain a path separator or "..",
    // which also rules out path traversal
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final KinoticStreamProperties properties;
    private final Vertx vertx;
    private final ConcurrentHashMap<String, StreamLog> streams = new ConcurrentHashMap<>();

    @Override
    public Future<StreamDefinition> createStreamIfNotExist(StreamDefinition definition) {
        return vertx.executeBlocking(() -> {
            requireValidName(definition.name());
            Validate.isTrue(definition.shardCount() > 0, "shardCount must be at least one");
            return streams.computeIfAbsent(definition.name(),
                                           name -> StreamLog.openOrCreate(streamDirectory(name), definition))
                          .definition();
        }, false);
    }

    @Override
    public Future<StreamPosition> append(String stream, String key, byte[] payload) {
        return vertx.executeBlocking(() -> {
            Validate.notNull(key, "key must not be null");
            Validate.notNull(payload, "payload must not be null");
            return streamLog(stream).append(key, payload);
        }, false);
    }

    @Override
    public Future<StreamSubscription> subscribe(String stream, String consumerName, StartPosition startPosition) {
        Context context = vertx.getOrCreateContext();
        return context.executeBlocking(() -> {
            requireValidName(consumerName);
            Validate.notNull(startPosition, "startPosition must not be null");
            return DefaultStreamSubscription.open(context, streamLog(stream), consumerName, startPosition);
        }, false);
    }

    @PreDestroy
    public void close() {
        streams.values().forEach(StreamLog::close);
        streams.clear();
    }

    private StreamLog streamLog(String stream) {
        requireValidName(stream);
        return streams.computeIfAbsent(stream, name -> StreamLog.open(streamDirectory(name), name));
    }

    private Path streamDirectory(String stream) {
        return Path.of(properties.getStream().getDataDirectory()).resolve(stream);
    }

    private static void requireValidName(String name) {
        Validate.isTrue(name != null && NAME_PATTERN.matcher(name).matches(),
                        "Invalid name '%s': use up to 128 letters, digits, '.', '_' or '-', starting with a letter or digit",
                        name);
    }
}
