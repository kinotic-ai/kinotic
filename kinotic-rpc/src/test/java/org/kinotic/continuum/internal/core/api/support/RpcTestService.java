

package org.kinotic.continuum.internal.core.api.support;

import io.vertx.core.Future;
import org.kinotic.rpc.api.annotations.Publish;
import org.kinotic.rpc.api.security.Participant;
import org.kinotic.continuum.internal.core.api.RpcTests;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.util.TokenBuffer;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to publish various service methods to be used by the {@link RpcTests}
 *
 * Created by navid on 10/30/19
 */
@Publish
public interface RpcTestService {

    List<String> LIST_OF_STRINGS = List.of("Bob", "Annie", "Wendy", "Nick", "Jose", "Joaquin", "Chaoxiang", "Johnny Blaze", "Sucka");
    SimpleObject STATIC_SIMPLE_OBJECT  = new SimpleObject().setFirstName("Johnny")
                                                           .setLastName("Blaze")
                                                           .setCount(10)
                                                           .setBigCount(10000000L);
    ABunchOfArgumentsHolder BUNCH_OF_ARGUMENTS = new ABunchOfArgumentsHolder(42, 23421432343242L, "Method Man", true, STATIC_SIMPLE_OBJECT, LIST_OF_STRINGS);
    /**
     * The value returned by getString below
     */
    String STRING_VALUE = "Hello Sucka!";

    ABunchOfArgumentsHolder acceptABunchOfArguments(int intValue,
                                                    long longValue,
                                                    String stringValue,
                                                    boolean boolValue,
                                                    SimpleObject simpleObject,
                                                    List<String> listOfStrings);

    String concatString(String lhs, String rhs);

    Mono<String> firstArgParticipant(Participant participant, String suffix);

    List<List<String>> getAListOfLists(List<List<String>> inputList);

    String getAnotherString();

    Flux<String> getInfiniteFlux();

    Flux<Integer> getLimitedFlux();

    List<String> getListOfStrings();

    Mono<String> getMonoEmptyString();

    Mono<String> getMonoFailure();

    Mono<Integer> getMonoIntegerNull();

    Mono<String> getMonoStringLiterallyNull();

    Mono<String> getMonoStringNull();

    Mono<String> getMonoWithValue();

    Mono<Void> getMonoWithVoidFromEmpty();

    Mono<Void> getMonoWithVoidFromNull();

    SimpleObject getSimpleObject();

    String getSimpleObjectToString(SimpleObject simpleObject);

    String getString();

    String getUnknownFailure();

    Future<String> getVertxFutureNullString();

    Mono<String> lastArgParticipant(String prefix, Participant participant);

    Mono<String> middleArgParticipant(String prefix, Participant participant, String suffix);

    List<String> modifyListOfStrings(String[] stringsToModify);

    Integer putListOfSimpleObjects(List<SimpleObject> simpleObjects);

    Integer putListOfStrings(List<String> strings);

    Integer putMapOfSimpleObjects(Map<String, SimpleObject> simpleObjects);

    Integer putNestedGenerics(List<Map<String, Set<SimpleObject>>> objects);

    String echoTokenBuffer(TokenBuffer tokenBuffer);

}
