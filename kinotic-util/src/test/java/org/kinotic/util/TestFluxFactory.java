package org.kinotic.util;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.ParallelFlux;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Navíd Mitchell 🤪 on 1/25/22.
 */
public class TestFluxFactory {

    @Test
    public void testSingleProducerMultiConsumer(){
        AtomicBoolean done = new AtomicBoolean(false);
        ParallelFlux<Integer> flux = FluxFactory.singleProducerMultiConsumer(sink -> {
            for(int i = 0; i < 10; i++){
                System.out.println("Sending data to sink "+i);
                sink.next(i);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            sink.complete();
        }, "Test", 5, 10);

        flux.subscribe(i -> System.out.println(Thread.currentThread().getName() + " -> " + i),
        t -> {
            System.out.println("Error: "+t.getMessage());
        }, () -> {
            done.set(true);
        });

        System.out.println("Just before wait");

        Awaitility.await()
                  .atMost(30, TimeUnit.SECONDS)
                  .untilTrue(done);

        System.out.println("Done");
    }

}
