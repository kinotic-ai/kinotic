package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.schema.AsyncC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.StreamC3Type;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Converts any async type registered with the {@link ReactiveAdapterRegistry}, such as {@link java.util.concurrent.CompletableFuture},
 * {@link reactor.core.publisher.Mono}, or {@link reactor.core.publisher.Flux}, to an {@link AsyncC3Type} or
 * {@link StreamC3Type} of its value type according to the adapter's cardinality.
 * Created by Navíd Mitchell 🤪 on 7/22/26
 */
@Component
public class ReactiveTypeConverter implements GenericTypeConverter {

    private final ReactiveAdapterRegistry adapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

    @Override
    public boolean supports(ResolvableType resolvableType) {
        Class<?> rawClass = resolvableType.getRawClass();
        return rawClass != null && adapterRegistry.getAdapter(rawClass) != null;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {

        ReactiveAdapter adapter = adapterRegistry.getAdapter(resolvableType.getRawClass());
        ResolvableType genericType = resolvableType.getGeneric(0);
        if(genericType.equals(ResolvableType.NONE)){
            throw new IllegalStateException(resolvableType + " is an async type but has no generic value type defined");
        }

        C3Type valueType = conversionContext.convert(genericType);
        return adapter.isMultiValue() ? new StreamC3Type(valueType) : new AsyncC3Type(valueType);
    }
}
