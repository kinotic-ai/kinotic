package org.kinotic.util;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;

import java.util.function.Consumer;

/**
 * Created by Navíd Mitchell 🤪 on 1/25/22.
 */
public class FluxFactory {

    public static <T> ParallelFlux<T> singleProducerMultiConsumer(Consumer<FluxSink<T>> dataProducer,
                                                                  String name,
                                                                  int parallelism,
                                                                  int prefetch){
        return Flux.create(dataProducer)
                    .subscribeOn(Schedulers.newSingle(name+"-producer"))
                    .parallel(parallelism, prefetch)
                    .runOn(Schedulers.newParallel(name+"-consumer", parallelism));
    }

}
