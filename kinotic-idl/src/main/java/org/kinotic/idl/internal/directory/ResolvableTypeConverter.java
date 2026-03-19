

package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.schema.C3Type;
import org.springframework.core.ResolvableType;

/**
 * Provides support for converting individual {@link ResolvableType}'s into the appropriate {@link C3Type}
 *
 *
 * Created by navid on 2019-06-13.
 */
public interface ResolvableTypeConverter {

    /**
     * Converts the given {@link ResolvableType} to the correct {@link C3Type}
     *
     * @param resolvableType to convert
     * @param conversionContext for this conversion process
     * @return the newly created {@link C3Type} for the class
     */
    C3Type convert(ResolvableType resolvableType,
                   ConversionContext conversionContext);

}
