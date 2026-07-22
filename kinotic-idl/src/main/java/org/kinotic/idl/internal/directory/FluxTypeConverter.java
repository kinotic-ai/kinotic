package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.StreamC3Type;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Converts a {@link Flux} to a {@link StreamC3Type} of its element type.
 * Created by Navíd Mitchell 🤪 on 7/22/26
 */
@Component
public class FluxTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {Flux.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {

        ResolvableType genericType = resolvableType.getGeneric(0);
        if(genericType.equals(ResolvableType.NONE)){
            throw new IllegalStateException("Flux found but no generic type defined");
        }

        return new StreamC3Type(conversionContext.convert(genericType));
    }
}
