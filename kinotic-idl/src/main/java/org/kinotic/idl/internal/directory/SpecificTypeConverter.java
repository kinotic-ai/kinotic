

package org.kinotic.idl.internal.directory;

/**
 * Provides a {@link ResolvableTypeConverter} that supports specific {@link Class} types.
 * This is useful for converting Classes that are not complex objects.
 * <p>
 *
 * Created by navid on 2019-06-14.
 */
public interface SpecificTypeConverter extends ResolvableTypeConverter {

    /**
     * @return the classes that can be converted by this {@link SpecificTypeConverter}
     */
    Class<?>[] supports();

}
