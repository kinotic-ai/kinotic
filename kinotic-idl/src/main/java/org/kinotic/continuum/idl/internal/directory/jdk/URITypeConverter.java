

package org.kinotic.continuum.idl.internal.directory.jdk;

import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.continuum.idl.api.schema.StringC3Type;
import org.kinotic.continuum.idl.internal.directory.ConversionContext;
import org.kinotic.continuum.idl.internal.directory.SpecificTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 *
 * Created by navid on 2019-06-14.
 */
@Component
public class URITypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {URI.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {
        return new StringC3Type();
    }

}
