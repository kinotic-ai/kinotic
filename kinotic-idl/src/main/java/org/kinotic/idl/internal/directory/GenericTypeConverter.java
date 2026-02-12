

package org.kinotic.idl.internal.directory;

import org.springframework.core.ResolvableType;

/**
 * Provides a {@link ResolvableTypeConverter} that supports arbitrary {@link ResolvableType} types.
 * <p>
 * This can be used for things like Converting all {@link Number} classes.
 * <p>
 * Created by navid on 2019-06-14.
 */
public interface GenericTypeConverter extends ResolvableTypeConverter {

    /**
     * Checks if the given {@link ResolvableType} is supported by this converter
     *
     * @param resolvableType to check if supported
     * @return true if this converter can convert the class false if not
     */
    boolean supports(ResolvableType resolvableType);

}
