

package org.kinotic.core.internal.api.support;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import org.kinotic.core.api.annotations.Proxy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.util.TokenBuffer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 *
 * Created by navid on 10/30/19
 */
@Proxy(namespace = "org.kinotic.core.internal.api.support",
       name = "RpcTestService")
public interface RpcTestServiceProxy {

    Mono<ABunchOfArgumentsHolder> acceptABunchOfArguments(int intValue,
                                                          long longValue,
                                                          String stringValue,
                                                          boolean boolValue,
                                                          SimpleObject simpleObject,
                                                          List<String> listOfStrings);

    Mono<String> concatString(String lhs, String rhs);

    /**
     * Declares one parameter fewer than {@link RpcTestService#concatWithOptionalSuffix(String, String)},
     * so an invocation through this proxy is what a client built before the second parameter existed sends.
     */
    Mono<String> concatWithOptionalSuffix(String value);

    Mono<String> firstArgParticipant(String suffix);

    Mono<List<List<String>>> getAListOfLists(List<List<String>> inputList);

    Future<String> getAnotherString();

    Mono<Buffer> getBuffer();

    Mono<byte[]> getByteArray();

    Flux<byte[]> getByteArrayFlux();

    Flux<String> getInfiniteFlux();

    Flux<Integer> getLimitedFlux();

    Mono<List<String>> getListOfStrings();

    Mono<String> getMissingRemoteMethodFailure();

    Mono<byte[]> getMonoByteArray();

    Mono<String> getMonoEmptyString();

    Mono<Integer> getMonoIntegerNull();

    Mono<String> getMonoStringLiterallyNull();

    Mono<String> getMonoStringNull();

    Mono<String> getMonoWithValue();

    Mono<Void> getMonoWithVoidFromEmpty();

    Mono<Void> getMonoWithVoidFromNull();

    Mono<SimpleObject> getSimpleObject();

    Mono<String> getSimpleObjectToString(SimpleObject simpleObject);

    CompletableFuture<String> getString();

    Mono<String> getUnknownFailure();

    Future<String> getVertxFutureNullString();

    Mono<String> lastArgParticipant(String prefix);

    Mono<String> middleArgParticipant(String prefix, String suffix);

    Mono<List<String>> modifyListOfStrings(List<String> stringsToModify);

    Mono<String> narrowParticipant();

    Mono<Integer> putListOfSimpleObjects(List<SimpleObject> simpleObjects);

    Mono<Integer> putListOfStrings(List<String> strings);

    Mono<Integer> putMapOfSimpleObjects(Map<String, SimpleObject> simpleObjects);

    Mono<Integer> putNestedGenerics(List<Map<String, Set<SimpleObject>>> objects);

    /**
     * Leaves off the primitive {@code times} parameter of {@link RpcTestService#repeatString(String, int)},
     * which no value can be omitted for.
     */
    Mono<String> repeatString(String value);

    Mono<String> echoTokenBuffer(TokenBuffer tokenBuffer);
}
