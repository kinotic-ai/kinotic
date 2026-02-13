

package org.kinotic.rpc.internal.api;

import io.vertx.core.Future;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.boot.api.Kinotic;
import org.kinotic.rpc.api.exceptions.RpcInvocationException;
import org.kinotic.rpc.api.exceptions.RpcMissingMethodException;
import org.kinotic.rpc.api.exceptions.RpcMissingServiceException;
import org.kinotic.rpc.internal.api.support.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 *
 * Created by navid on 10/30/19
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class RpcTests {

    @Autowired
    private Kinotic kinotic;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // these are not detected because continuum wires them..
    @Autowired
    private NonExistentServiceProxy nonExistentServiceProxy;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // these are not detected because continuum wires them..
    @Autowired
    private RpcTestServiceProxy rpcTestServiceProxy;

    @Autowired
    private JsonMapper jsonMapper;

    // TODO: test to few arguments, and too many arguments, also a variation with the participant. Participant variant error message may be misleading?
    // See org.kinotic.rpc.internal.api.service.json.AbstractJacksonSupport Line 114, Line 180. Should we keep the number of participant args in mind.

    @Test
    public void testABunchOfArguments(){
        ABunchOfArgumentsHolder argumentsHolder = RpcTestService.BUNCH_OF_ARGUMENTS;
        Mono<ABunchOfArgumentsHolder> mono = rpcTestServiceProxy.acceptABunchOfArguments(argumentsHolder.getIntValue(),
                                                                                         argumentsHolder.getLongValue(),
                                                                                         argumentsHolder.getStringValue(),
                                                                                         argumentsHolder.isBoolValue(),
                                                                                         argumentsHolder.getSimpleObject(),
                                                                                         RpcTestService.LIST_OF_STRINGS);

        StepVerifier.create(mono)
                    .expectNext(argumentsHolder)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testSendTwoArgsReturnOne(){
        String lhs = "Hello ";
        String rhs = "Wat";
        Mono<String> mono = rpcTestServiceProxy.concatString(lhs, rhs);

        StepVerifier.create(mono)
                    .expectNext(lhs + rhs)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testFirstArgParticipant(){
        String suffix = " Wat";
        Mono<String> mono = rpcTestServiceProxy.firstArgParticipant(suffix);

        StepVerifier.create(mono)
                    .expectNext(kinotic.serverInfo().getNodeName() + suffix)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testInfiniteFlux(){
        Flux<String> flux = rpcTestServiceProxy.getInfiniteFlux();

        StepVerifier.create(flux)
                    .expectNextMatches(s -> s.startsWith("Hello Sucka"))
                    .expectNextMatches(s -> s.startsWith("Hello Sucka"))
                    .expectNextMatches(s -> s.startsWith("Hello Sucka"))
                    .expectNextMatches(s -> s.startsWith("Hello Sucka"))
                    .thenCancel()
                    .verify();
    }

    @Test
    public void testLastArgParticipant(){
        String prefix = "Hello ";

        Mono<String> mono = rpcTestServiceProxy.lastArgParticipant(prefix);

        StepVerifier.create(mono)
                    .expectNext(prefix + kinotic.serverInfo().getNodeName())
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testLimitedFlux(){
        Flux<Integer> flux = rpcTestServiceProxy.getLimitedFlux();

        StepVerifier.create(flux)
                    .expectNext(1, 2, 3, 4, 5)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testMiddleArgParticipant(){
        String prefix = "Hello ";
        String suffix = " Wat";

        Mono<String> mono = rpcTestServiceProxy.middleArgParticipant(prefix, suffix);

        StepVerifier.create(mono)
                    .expectNext(prefix + kinotic.serverInfo().getNodeName() + suffix)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testMissingRemoteMethodFailure() {
        Mono<String> mono = rpcTestServiceProxy.getMissingRemoteMethodFailure();

        StepVerifier.create(mono)
                    .expectError(RpcMissingMethodException.class)
                    .verify();
    }

    @Test
    public void testMissingServiceFailure() {
        Mono<Void> mono = nonExistentServiceProxy.probablyNotHome();

        StepVerifier.create(mono)
                    .expectError(RpcMissingServiceException.class)
                    .verify();
    }

    @Test
    public void testMonoEmptyString() {
        Mono<String> mono = rpcTestServiceProxy.getMonoEmptyString();

        StepVerifier.create(mono)
                    .expectNext("")
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testMonoIntegerNull() {
        AtomicBoolean hasNull = new AtomicBoolean();
        Mono<Integer> mono = rpcTestServiceProxy
                .getMonoIntegerNull()
                .doOnSuccess(v -> {
                    if (v == null) hasNull.set(true);
                });

        StepVerifier.create(mono)
                    .expectComplete()
                    .verify();

        Assertions.assertTrue(hasNull.get());
    }

    @Test
    public void testMonoStringLiterallyNull() {
        Mono<String> mono = rpcTestServiceProxy.getMonoStringLiterallyNull();

        StepVerifier.create(mono)
                    .expectNext("null")
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testMonoStringNull() {
        AtomicBoolean hasNull = new AtomicBoolean();
        Mono<String> mono = rpcTestServiceProxy
                .getMonoStringNull()
                .doOnSuccess(v -> {
                    if (v == null) hasNull.set(true);
                });

        StepVerifier.create(mono)
                    .expectComplete()
                    .verify();

        Assertions.assertTrue(hasNull.get());
    }

    @Test
    public void testMonoWithValue() {
        Mono<String> mono = rpcTestServiceProxy.getMonoWithValue();

        StepVerifier.create(mono)
                    .expectNext("Hello Bob")
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testMonoWithVoidFromEmpty() {
        Mono<Void> mono = rpcTestServiceProxy.getMonoWithVoidFromEmpty();
        StepVerifier.create(mono)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testMonoWithVoidFromNull() {
        Mono<Void> mono = rpcTestServiceProxy.getMonoWithVoidFromNull();
        StepVerifier.create(mono)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testMultipleRequests(){
        Mono<String> mono = Mono.fromFuture(rpcTestServiceProxy.getString());

        StepVerifier.create(mono).expectNext(RpcTestService.STRING_VALUE).expectComplete().verify();

        Mono<String> mono2 = Mono.fromFuture(rpcTestServiceProxy.getString());

        StepVerifier.create(mono2).expectNext(RpcTestService.STRING_VALUE).expectComplete().verify();

        Mono<String> mono3 = Mono.fromFuture(rpcTestServiceProxy.getString());

        StepVerifier.create(mono3).expectNext(RpcTestService.STRING_VALUE).expectComplete().verify();
    }

    @Test
    public void testNestedArrays(){

        List<List<String>> input = new ArrayList<>();
        input.add(RpcTestService.LIST_OF_STRINGS);
        input.add(RpcTestService.LIST_OF_STRINGS);
        input.add(RpcTestService.LIST_OF_STRINGS);
        Mono<List<List<String>>> mono = rpcTestServiceProxy.getAListOfLists(input);
        StepVerifier.create(mono)
                    .expectNext(input.stream().map(strings -> strings.stream().map(s -> "Hello "+ s).collect(Collectors.toList())).collect(Collectors.toList()))
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testPutListOfSimpleObjects(){
        List<SimpleObject> simpleObjects = new ArrayList<>();
        for(int i = 0; i < 10; i++){
            simpleObjects.add(RpcTestService.STATIC_SIMPLE_OBJECT);
        }
        Mono<Integer> mono = rpcTestServiceProxy.putListOfSimpleObjects(simpleObjects);
        StepVerifier.create(mono)
                    .expectNext(10)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testPutMapOfSimpleObjects(){
        Map<String, SimpleObject> simpleObjects = new HashMap<>();
        for(int i = 0; i < 10; i++){
            simpleObjects.put(UUID.randomUUID().toString(), RpcTestService.STATIC_SIMPLE_OBJECT);
        }
        Mono<Integer> mono = rpcTestServiceProxy.putMapOfSimpleObjects(simpleObjects);
        StepVerifier.create(mono)
                    .expectNext(10)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testPutNestedGenerics(){
        List<Map<String, Set<SimpleObject>>> toSend = new ArrayList<>();
        for(int x = 0; x < 2; x++){
            Map<String, Set<SimpleObject>> simpleObjectsMap = new HashMap<>();
            for(int i = 0; i < 5; i++){

                Set<SimpleObject> simpleObjectSet = new HashSet<>();
                for(int o = 0; o < 10; o++){
                    SimpleObject obj = new SimpleObject().setFirstName("Johnny")
                                                         .setLastName("Blaze_" + o)
                                                         .setCount(10)
                                                         .setBigCount(10000000L);
                    simpleObjectSet.add(obj);
                }

                simpleObjectsMap.put(UUID.randomUUID().toString(),
                                     simpleObjectSet);
            }
            toSend.add(simpleObjectsMap);
        }

        Mono<Integer> mono = rpcTestServiceProxy.putNestedGenerics(toSend);
        StepVerifier.create(mono)
                    .expectNext(100)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testReceiveCollection(){
        Mono<List<String>> mono = rpcTestServiceProxy.getListOfStrings();
        StepVerifier.create(mono)
                    .expectNext(RpcTestService.LIST_OF_STRINGS)
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testRpcCompletableFutureString(){
        CompletableFuture<String> mono = rpcTestServiceProxy.getString();

        StepVerifier.create(Mono.fromFuture(mono)).expectNext(RpcTestService.STRING_VALUE).expectComplete().verify();
    }

    @Test
    public void testSendAndReceiveCollection(){
        Mono<List<String>> mono = rpcTestServiceProxy.modifyListOfStrings(RpcTestService.LIST_OF_STRINGS);
        StepVerifier.create(mono)
                    .expectNext(RpcTestService.LIST_OF_STRINGS.stream().map(s -> "Hello "+ s).collect(Collectors.toList()))
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testSendCollection(){
        Mono<Integer> mono = rpcTestServiceProxy.putListOfStrings(RpcTestService.LIST_OF_STRINGS);
        StepVerifier.create(mono)
                    .expectNext(RpcTestService.LIST_OF_STRINGS.size())
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testSendTokenBuffer() {
        try (TokenBuffer tokenBuffer = new TokenBuffer(jsonMapper._serializationContext(), false)) {
            tokenBuffer.writeStartObject();
            tokenBuffer.writeStringProperty("test", "Hello Sucka");
            tokenBuffer.writeEndObject();

            Mono<String> mono = rpcTestServiceProxy.echoTokenBuffer(tokenBuffer);
            StepVerifier.create(mono)
                        .expectNext("{\"test\":\"Hello Sucka\"}")
                        .expectComplete()
                        .verify();
        }
    }

    @Test
    public void testSimpleObject(){
        Mono<Tuple2<SimpleObject, String>> mono = rpcTestServiceProxy.getSimpleObject()
                                                                     .zipWhen(simpleObject -> rpcTestServiceProxy.getSimpleObjectToString(simpleObject));

        StepVerifier.create(mono)
                    .expectNextMatches(tuple -> tuple.getT1().toString().equals(tuple.getT2()))
                    .expectComplete()
                    .verify();
    }

    @Test
    public void testUnknownFailure(){
        Mono<String> mono = rpcTestServiceProxy.getUnknownFailure();

        StepVerifier.create(mono).expectErrorMatches(throwable -> {
            boolean ret = false;
            // When the original exception class cannot be instantiated you wll get a RpcInvocationException
            if(throwable instanceof RpcInvocationException){
                ret = Objects.equals(((RpcInvocationException) throwable).getOriginalClassName(),
                                     "org.kinotic.continuum.internal.core.api.support.DefaultRpcTestService$UnknownThrowable");
            }
            return ret;
        }).verify();
    }

    @Test
    public void testVertxFuture(){
        Future<String> future  = rpcTestServiceProxy.getAnotherString();

        Awaitility.await().until(future::isComplete);

        if(future.failed()){
            throw new IllegalStateException("TestServiceProxy method invocation failed", future.cause());
        }else if(!future.result().equals(RpcTestService.STRING_VALUE)){
            throw new IllegalStateException("Service data returned does not match what was expected: "+RpcTestService.STRING_VALUE+" got: "+future.result());
        }
    }

    @Test
    public void testVertxFutureNullString(){
        Future<String> future  = rpcTestServiceProxy.getVertxFutureNullString();

        Awaitility.await().until(future::isComplete);

        if(future.failed()){
            throw new IllegalStateException("TestServiceProxy method invocation failed", future.cause());
        }else if(!(future.result() == null)){
            throw new IllegalStateException("Service data returned does not match what was expected: null got: "+future.result());
        }
    }

}
