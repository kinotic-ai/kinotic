package org.kinotic.idl.internal.api.converter;

import org.apache.commons.lang3.Validate;
import org.kinotic.idl.api.converter.C3ConversionException;
import org.kinotic.idl.api.converter.C3ConversionContext;
import org.kinotic.idl.api.converter.C3TypeConverter;
import org.kinotic.idl.api.converter.Cacheable;
import org.kinotic.idl.api.converter.IdlConverterStrategy;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ComplexC3Type;
import org.kinotic.idl.api.schema.ReferenceC3Type;

import java.util.*;

/**
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
public class DefaultC3ConversionContext<R, S> implements C3ConversionContext<R, S> {

    private final IdlConverterStrategy<R, S> strategy;

    private final Deque<C3Type> conversionDepthStack = new ArrayDeque<>();

    private final Map<C3Type, R> cache = new HashMap<>();

    private final S state;

    public DefaultC3ConversionContext(IdlConverterStrategy<R, S> strategy) {
        this.strategy = strategy;
        this.state = strategy.initialState();
    }

    @Override
    public R convert(C3Type c3Type) {
        Validate.notNull(c3Type, "C3Type must not be null");

        //FIXME: add JSR-380 validation

        conversionDepthStack.addFirst(c3Type);
        try {
            @SuppressWarnings("unchecked")
            C3TypeConverter<R, C3Type, S> converter = (C3TypeConverter<R, C3Type, S>) findConverter(c3Type);
            Validate.isTrue(converter != null,
                            "No C3TypeConverter can be found for " + c3Type.getClass().getName()
                                    + " When using strategy " + strategy.getClass().getName());

            boolean cache = strategy.shouldCache() && converter instanceof Cacheable;
            R result = null;

            if(cache){
                result = this.cache.get(c3Type);
            }
            if(result == null) {
                result = converter.convert(c3Type, this);
                if (cache) {
                    this.cache.put(c3Type, result);
                }
            }
            return result;
        } catch (C3ConversionException e) {
            // already carries the conversion chain, stamped at the deepest frame where the stack was complete
            throw e;
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new C3ConversionException(message + "\n  while converting: " + conversionChain(), e);
        } finally {
            conversionDepthStack.removeFirst();
        }
    }

    @Override
    public S state() {
        return state;
    }

    private C3TypeConverter<R, ? extends C3Type, S> findConverter(C3Type c3Type) {
        for (C3TypeConverter<R, ? extends C3Type, S> converter : strategy.converters()) {
            if (converter.supports(c3Type)) {
                return converter;
            }
        }
        return null;
    }

    private String conversionChain() {
        StringBuilder sb = new StringBuilder();
        // conversionDepthStack is newest-first; render root-first so the chain reads top-down
        for (Iterator<C3Type> it = conversionDepthStack.descendingIterator(); it.hasNext(); ) {
            sb.append(describe(it.next()));
            if (it.hasNext()) {
                sb.append(" > ");
            }
        }
        return sb.toString();
    }

    private static String describe(C3Type c3Type) {
        String name = null;
        if (c3Type instanceof ComplexC3Type complexC3Type) {
            name = complexC3Type.getQualifiedName();
        } else if (c3Type instanceof ReferenceC3Type referenceC3Type) {
            name = referenceC3Type.getQualifiedName();
        }
        return name != null ? c3Type.getClass().getSimpleName() + "(" + name + ")"
                            : c3Type.getClass().getSimpleName();
    }

}
