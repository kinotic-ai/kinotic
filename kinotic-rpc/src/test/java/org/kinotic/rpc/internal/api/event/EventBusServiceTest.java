

package org.kinotic.rpc.internal.api.event;

import org.kinotic.rpc.api.event.Event;
import org.kinotic.rpc.api.event.EventBusService;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by navid on 11/6/19
 */
//@ExtendWith(SpringExtension.class)
//@SpringBootTest
//@ActiveProfiles("test")
public class EventBusServiceTest {

    // There are no hard constraints on destinations we are choosing these because of internal conventions
    private static final String DESTINATION = "srv://org.kinotic.rpc.tests.TestService/serviceMethod";

    @Autowired
    private EventBusService eventBusService;

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();


    public void testEventBusStringData(){
        Event<byte[]> eventConstant = Event.create(DESTINATION, "Hello SuckaAA!".getBytes(StandardCharsets.UTF_8));
        Flux<Event<byte[]>> flux =  eventBusService.listen(DESTINATION);

        executorService.schedule(() -> {
            eventBusService.sendWithAck(eventConstant).subscribe();
        }, 1, TimeUnit.SECONDS);

        StepVerifier.create(flux)
                    .expectNextMatches(event -> { // equality comparison does not work so..
                        boolean ret = event.cri().equals(eventConstant.cri())
                                && Arrays.equals(event.data(), eventConstant.data());
                        return ret;
                    })
                    .thenCancel()
                    .verify();
    }


    public void testEventBusImmediateStringData(){
        Event<byte[]> eventConstant = Event.create(DESTINATION, "Hello Sucka!".getBytes(StandardCharsets.UTF_8));
        Mono<Flux<Event<byte[]>>> fluxImmediate = eventBusService.listenWithAck(DESTINATION);

        Flux<Event<byte[]>> flux = fluxImmediate.block();

        executorService.schedule(() -> {
            eventBusService.sendWithAck(eventConstant).subscribe();
        }, 1, TimeUnit.SECONDS);

        assert flux != null;
        StepVerifier.create(flux)
                    .expectNextMatches(event -> { // equality comparison does not work so..
                        boolean ret = event.cri().equals(eventConstant.cri())
                                && Arrays.equals(event.data(), eventConstant.data());
                        return ret;
                    })
                    .thenCancel()
                    .verify();
    }

}
