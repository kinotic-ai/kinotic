package org.kinotic.idl.internal.api.converter;

import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.converter.C3ConversionContext;
import org.kinotic.idl.api.converter.IdlConverter;
import org.kinotic.idl.api.converter.IdlConverterStrategy;

/**
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
public class DefaultIdlConverter<R, S> implements IdlConverter<R, S> {

    private final C3ConversionContext<R, S> conversionContext;

    public DefaultIdlConverter(IdlConverterStrategy<R, S> strategy) {
        this.conversionContext = new DefaultC3ConversionContext<>(strategy);
    }

    @Override
    public R convert(C3Type type) {
        return conversionContext.convert(type);
    }

    @Override
    public C3ConversionContext<R, S> getConversionContext() {
        return conversionContext;
    }
}
