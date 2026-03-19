

package org.kinotic.idl.internal.directory.jdk;

import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.VoidC3Type;
import org.kinotic.idl.internal.directory.ConversionContext;
import org.kinotic.idl.internal.directory.SpecificTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 *
 * Created by navid on 2019-07-25.
 */
@Component
public class VoidTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {void.class, Void.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType, ConversionContext conversionContext) {
        return new VoidC3Type();
    }
}
