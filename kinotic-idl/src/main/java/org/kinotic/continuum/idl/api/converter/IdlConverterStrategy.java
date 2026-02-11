package org.kinotic.continuum.idl.api.converter;

import org.kinotic.continuum.idl.api.schema.C3Type;

import java.util.Set;

/**
 * The {@link IdlConverterStrategy} is used to determine how to convert a Continuum IDL to a specific language type.
 * The {@link IdlConverterStrategy} should be reusable and thread safe.
 *
 * @param <R> The type to convert to
 * @param <S> The state type
 *s4/26/23.
 */
public interface IdlConverterStrategy<R, S> {

    /**
     * Returns the {@link C3TypeConverter}s that are supported by this strategy.
     * @return the supported {@link C3TypeConverter}s
     */
    Set<C3TypeConverter<R, ? extends C3Type, S>> converters();

    /**
     * The object that will be available via the {@link C3ConversionContext#state()}.
     * This can be a simple {@link java.util.Map} or something with better type safety.
     * This should return a new instance each time it is called.
     * This will be called each time a new {@link C3ConversionContext} is created.
     * @return the conversion context state.
     */
    S initialState();

    /**
     * Determines if caching is turned on for this strategy.
     * If shouldCache is true and the {@link C3TypeConverter} extends {@link Cacheable} then the results of the conversion will be cached and reused
     * @return true if the results of the converts should be cached
     */
    boolean shouldCache();

}

