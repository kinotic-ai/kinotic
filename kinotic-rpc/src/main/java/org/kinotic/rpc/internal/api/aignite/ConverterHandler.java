

package org.kinotic.rpc.internal.api.aignite;

import io.vertx.core.Handler;

import java.util.function.Function;

/**
 * Created by Navid Mitchell on 8/3/17.
 */
public class ConverterHandler<T,R> implements Handler<T>{
    private final Function<T,R> converter;
    private final Handler<R> handlerConvertedValues;

    public ConverterHandler(Function<T, R> converter, Handler<R> handlerConvertedValues) {
        this.converter = converter;
        this.handlerConvertedValues = handlerConvertedValues;
    }

    @Override
    public void handle(T event) {
        handlerConvertedValues.handle(converter.apply(event));
    }

}
