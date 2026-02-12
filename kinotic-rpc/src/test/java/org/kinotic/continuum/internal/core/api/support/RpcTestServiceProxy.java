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

package org.kinotic.continuum.internal.core.api.support;

import io.vertx.core.Future;
import org.kinotic.rpc.api.annotations.Proxy;
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
@Proxy(namespace = "org.kinotic.continuum.internal.core.api.support",
       name = "RpcTestService")
public interface RpcTestServiceProxy {

    Mono<ABunchOfArgumentsHolder> acceptABunchOfArguments(int intValue,
                                                          long longValue,
                                                          String stringValue,
                                                          boolean boolValue,
                                                          SimpleObject simpleObject,
                                                          List<String> listOfStrings);

    Mono<String> concatString(String lhs, String rhs);

    Mono<String> firstArgParticipant(String suffix);

    Mono<List<List<String>>> getAListOfLists(List<List<String>> inputList);

    Future<String> getAnotherString();

    Flux<String> getInfiniteFlux();

    Flux<Integer> getLimitedFlux();

    Mono<List<String>> getListOfStrings();

    Mono<String> getMissingRemoteMethodFailure();

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

    Mono<Integer> putListOfSimpleObjects(List<SimpleObject> simpleObjects);

    Mono<Integer> putListOfStrings(List<String> strings);

    Mono<Integer> putMapOfSimpleObjects(Map<String, SimpleObject> simpleObjects);

    Mono<Integer> putNestedGenerics(List<Map<String, Set<SimpleObject>>> objects);

    Mono<String> echoTokenBuffer(TokenBuffer tokenBuffer);
}
