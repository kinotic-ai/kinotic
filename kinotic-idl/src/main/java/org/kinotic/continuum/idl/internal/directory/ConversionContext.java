

package org.kinotic.continuum.idl.internal.directory;

import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.continuum.idl.api.schema.ComplexC3Type;
import org.kinotic.continuum.idl.api.schema.ReferenceC3Type;
import org.springframework.core.ResolvableType;

import java.util.Set;

/**
 * Represents the current state of the conversion as well as providing a means for converters to convert dependent {@link C3Type}'s
 * <p>
 * Created by navid on 2019-06-28.
 */
public interface ConversionContext {

    /**
     * Converts the given resolvable type into a {@link C3Type} that is depended on by another {@link C3Type}
     * This will return a {@link ReferenceC3Type} when appropriate
     * @param resolvableType to convert
     * @return the {@link C3Type} representing the {@link ResolvableType}
     */
    C3Type convert(ResolvableType resolvableType);

    /**
     * @return all of the {@link ComplexC3Type} known to this {@link ConversionContext}
     */
    Set<ComplexC3Type> getComplexC3Types();

}
