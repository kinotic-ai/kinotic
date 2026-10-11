

package org.kinotic.idl.internal.directory.jdk;

import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.DateC3Type;
import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.SpecificTypeConverter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/**
 *
 * Created by navid on 2019-06-14.
 */
@Component
public class DateToC3Type implements SpecificTypeConverter {

    // an Instant travels as an ISO-8601 string, as a Date does
    private static final Class<?>[] supports = {Date.class, Instant.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {
        return new DateC3Type();
    }
}
