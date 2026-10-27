package org.kinotic.core.api.utils;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import lombok.extern.slf4j.Slf4j;
import net.openhft.hashing.LongTupleHashFunction;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.internal.utils.MetaUtil;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.api.annotations.Proxy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.exceptions.RpcMissingServiceException;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.ReactiveTypeDescriptor;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * Created by Navid Mitchell on 6/10/20
 */
@Slf4j
public class KinoticUtil {

    public static String safeEncodeURI(String uri){
        String encoded = URLEncoder.encode(uri, StandardCharsets.UTF_8);
        return encoded.replace("_", "-");
    }

    /**
     * Bridges a {@link CompletableFuture} into a Vert.x {@link Future} on the caller's context, so everything
     * composed after it runs where the caller runs and a context-local such as the current participant stays
     * observable across the hop. A failure is delivered as its raw cause, never wrapped in a
     * {@link CompletionException}. Outside any Vert.x context, handlers run on the completing thread.
     */
    public static <T> Future<T> toFuture(CompletableFuture<T> stage) {
        // a failure that crossed a dependent stage arrives CompletionException-wrapped; strip it here
        // so every Vert.x consumer sees the raw cause. A bare Promise.promise() would not re-dispatch
        // onto the context, so the context-bound fromCompletionStage overload is required.
        CompletableFuture<T> unwrapped = new CompletableFuture<>();
        stage.whenComplete((result, err) -> {
            if (err != null) {
                unwrapped.completeExceptionally(err instanceof CompletionException && err.getCause() != null
                                                        ? err.getCause() : err);
            } else {
                unwrapped.complete(result);
            }
        });
        Context ctx = Vertx.currentContext();
        Future<T> ret;
        if (ctx != null) {
            ret = Future.fromCompletionStage(unwrapped, ctx);
        } else {
            ret = Future.fromCompletionStage(unwrapped);
        }
        return ret;
    }

    /**
     * Maps a failed RPC send to the exception the caller should observe. A {@link ReplyFailure#NO_HANDLERS}
     * failure becomes {@link RpcMissingServiceException}; any other failure is returned unchanged.
     * When a {@link ServiceDirectory} is provided, a NO_HANDLERS failure is also reported to it, so every
     * RPC send doubles as a liveness probe and the directory self-heals from ordinary traffic.
     * @param throwable the failure the send completed with
     * @param destination the CRI the send was addressed to
     * @param serviceDirectory the directory to report unreachability to, or null when none is configured
     * @return the throwable to propagate to the caller
     */
    public static Throwable mapSendFailure(Throwable throwable, CRI destination, ServiceDirectory serviceDirectory) {
        Throwable ret = throwable;
        if (throwable instanceof ReplyException replyException) {
            if (replyException.failureType() == ReplyFailure.NO_HANDLERS) {
                if (serviceDirectory != null) {
                    // fire-and-forget: reportUnreachable debounces and only writes verified state
                    serviceDirectory.reportUnreachable(destination.raw())
                                    .onFailure(reportFailure -> log.debug("Failed to report unreachable service {}",
                                                                          destination.raw(), reportFailure));
                }
                ret = new RpcMissingServiceException(throwable);
            } else if (replyException.failureType() == ReplyFailure.TIMEOUT) {
                // the consumer acknowledges before it dispatches, so a lost acknowledgement cannot rule out
                // that the handler ran
                ret = new RpcServiceUnavailableException("No acknowledgement for the request to " + destination.raw(), throwable);
            }
        }
        return ret;
    }

    /**
     * The identity a service interface addresses: its zone, namespace, name and version as declared on the
     * interface, with no scope. A scoped call adds the scope per invocation. The interface is either a
     * {@link Proxy} for a service published elsewhere or the {@link Publish}ed interface itself, whose proxies
     * then address the service it declares.
     * @param serviceInterface an interface annotated with {@link Proxy} or {@link Publish}
     * @return the identifier the interface's calls are routed by
     * @throws IllegalArgumentException if the interface carries neither annotation or declares no version
     */
    public static ServiceIdentifier serviceIdentifierOf(Class<?> serviceInterface) {
        return serviceIdentifierOf(serviceInterface, null);
    }

    /**
     * The identity a service interface addresses within a scope: its zone, namespace, name and version as
     * declared on the interface, and the given scope.
     * @param serviceInterface an interface annotated with {@link Proxy} or {@link Publish}
     * @param scope the scope the service is addressed in, or null for the un-scoped address
     * @return the identifier the interface's calls are routed by
     * @throws IllegalArgumentException if the interface carries neither annotation or declares no version
     */
    public static ServiceIdentifier serviceIdentifierOf(Class<?> serviceInterface, String scope) {
        String namespace;
        String name;
        // a published interface is addressed as it is registered, by the @Publish found the way the registration
        // finds it; a @Proxy counts only where it is declared
        Publish publishAnnotation = AnnotationUtils.findAnnotation(serviceInterface, Publish.class);
        Proxy proxyAnnotation = serviceInterface.getAnnotation(Proxy.class);
        if (publishAnnotation != null) {
            namespace = publishAnnotation.namespace();
            name = publishAnnotation.name();
        } else if (proxyAnnotation != null) {
            namespace = proxyAnnotation.namespace();
            name = proxyAnnotation.name();
        } else {
            throw new IllegalArgumentException("The interface " + serviceInterface.getName() + " must be annotated with @Proxy or @Publish");
        }
        String version = MetaUtil.getVersion(serviceInterface);
        Validate.isTrue(StringUtils.isNotBlank(version),
                        "The interface %s must declare a @Version, on itself or in its package-info.java",
                        serviceInterface.getName());
        // The declared zone addresses the service; with no declaration it is the un-zoned address, whose
        // reachability is whatever the gateway's routing rules allow
        return new ServiceIdentifier(MetaUtil.getZone(serviceInterface),
                                     safeEncodeURI(namespace.isEmpty() ? serviceInterface.getPackageName() : namespace),
                                     name.isEmpty() ? serviceInterface.getSimpleName() : name,
                                     scope,
                                     version);
    }

    /**
     * Registers the Vert.x {@link Future} with the registry as a single, optional value reactive type, so the
     * service invoker and the service proxies treat a {@code Future<T>} like a {@code Mono<T>}: a {@code Future}
     * result replies when it completes, and a {@code Future<T>} return type converts a reply into its {@code T}.
     * @param reactiveAdapterRegistry the registry the invoker and the proxies resolve reactive types with
     */
    public static void registerVertxFutureAdapter(ReactiveAdapterRegistry reactiveAdapterRegistry) {
        reactiveAdapterRegistry.registerReactiveType(ReactiveTypeDescriptor.singleOptionalValue(Future.class,
                                                                                                (Supplier<Future<?>>) Future::succeededFuture),
                                                     source -> {
                                                         Future<?> future = (Future<?>) source;
                                                         // Short-circuit for already-completed futures to avoid Vert.x
                                                         // context dispatch. Both future.onComplete() and toCompletionStage()
                                                         // dispatch through the event loop even for resolved futures.
                                                         if (future.isComplete()) {
                                                             if (future.succeeded()) {
                                                                 Object result = future.result();
                                                                 return result != null ? Mono.just(result) : Mono.empty();
                                                             } else {
                                                                 return Mono.error(future.cause());
                                                             }
                                                         }
                                                         return Mono.fromCompletionStage(future.toCompletionStage());
                                                     },
                                                     publisher -> Future.future(promise -> Mono.from(publisher)
                                                                                               .doOnSuccess((Consumer<Object>) o -> {
                                                                                                   if(o != null){
                                                                                                       promise.complete(o);
                                                                                                   }else{
                                                                                                       promise.complete();
                                                                                                   }
                                                                                               })
                                                                                               .subscribe(v -> {}, promise::fail)));// We use an empty consumer this is handled with doOnSuccess, this is done so we get a single "signal" instead of onNext, onComplete type logic..
    }

    /**
     * Returns the MCP tool name for a service function: the XXHash128 of the service's qualified name plus
     * the function name, in base 36. A qualified name is unique system wide, so its hash is too, and base 36
     * keeps the result to 25 characters or fewer whatever the service is called.
     * This means the tool name called by an LLM will always be an allowed character set and should never collide with other tools.
     * Tool names are minted here and never parsed back apart.
     * @param qualifiedName the service's qualified name, as {@code ServiceIdentifier.qualifiedName()} returns
     *                      it, such as {@code management-api~org.kinotic.management.api.services.ProjectService}
     * @param functionName the name of the function the tool calls
     * @return the tool name, made only of {@code [0-9a-z]}
     */
    public static String mcpToolName(String qualifiedName, String functionName) {
        long[] hash = LongTupleHashFunction.xx128().hashChars(qualifiedName + "/" + functionName);
        byte[] bytes = ByteBuffer.allocate(Long.BYTES * 2).putLong(hash[0]).putLong(hash[1]).array();
        // signum 1 reads the 16 bytes as an unsigned 128-bit magnitude, so a high bit set in hash[0] stays a
        // positive value rather than becoming a two's-complement negative one
        return new BigInteger(1, bytes).toString(36);
    }

    public static int getRandomNumberInRange(int max) {
        Random r = new Random();
        return r.ints(0, (max + 1)).findFirst().orElseThrow();
    }

    /**
     * Provides a {@link LinearConverter<Long>} to convert longs for the provided ranges
     * @param oldMin the old range min
     * @param oldMax the old range max
     * @param newMin the new range min
     * @param newMax the new range max
     * @return the new {@link LinearConverter<Long>} that can be used for conversion
     */
    public static LinearConverter<Long> linearConverter(long oldMin, long oldMax, long newMin, long newMax){
        return new LongLinearConverter(oldMin, oldMax, newMin, newMax);
    }


    public interface LinearConverter<T extends Number> {
        T convert(T value);
    }

    public static class LongLinearConverter implements LinearConverter<Long>{

        private final Function<Long, Long> conversionFunction;

        public LongLinearConverter(long oldMin, long oldMax, long newMin, long newMax) {

            long oldRange = (oldMax - oldMin);
            if (oldRange == 0) {
                conversionFunction = (oldValue) -> newMin;
            } else {
                long newRange = newMax - newMin;
                conversionFunction = (oldValue) -> (((oldValue - oldMin) * newRange) / oldRange) + newMin;
            }
        }

        public Long convert(Long value){
            return conversionFunction.apply(value);
        }
    }

}
