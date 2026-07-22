package org.kinotic.idl.internal.directory.jdk;

import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.internal.directory.ConversionContext;
import org.kinotic.idl.internal.directory.SpecificTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Converts a {@link CompletableFuture} to the {@link C3Type} of its value: the future is the invocation
 * mechanics, the value is the contract.
 * Created by Navíd Mitchell 🤪 on 7/22/26
 */
@Component
public class CompletableFutureTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {CompletableFuture.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {

        ResolvableType genericType = resolvableType.getGeneric(0);
        if(genericType.equals(ResolvableType.NONE)){
            throw new IllegalStateException("CompletableFuture found but no generic type defined");
        }

        return conversionContext.convert(genericType);
    }
}
