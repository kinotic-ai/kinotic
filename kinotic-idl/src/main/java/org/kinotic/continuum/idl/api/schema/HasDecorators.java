package org.kinotic.continuum.idl.api.schema;

import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;

import java.util.List;

/**
 * Defines a type that can have {@link C3Decorator}s
 * Created by Navíd Mitchell 🤪 on 4/19/24.
 */
public interface HasDecorators {

    /**
     * Checks if this type contains a {@link C3Decorator} of the given subclass
     *
     * @param clazz to see if this type has
     * @return true if the {@link C3Decorator} is present false if not
     */
    default boolean containsDecorator(Class<? extends C3Decorator> clazz){
        return findDecorator(clazz) != null;
    }

    /**
     * Checks if this type has any {@link C3Decorator}
     *
     * @return true if any {@link C3Decorator}s are present false if not
     */
    default boolean hasDecorators(){
        return getDecorators() != null && !getDecorators().isEmpty();
    }

    /**
     * Finds the first {@link C3Decorator} of the given subclass or null if none are found
     *
     * @param clazz to find the {@link C3Decorator} for
     * @return the {@link C3Decorator} or null if none are found
     */
    default <T extends C3Decorator> T findDecorator(Class<T> clazz){
        T ret = null;
        if(getDecorators() != null){
            for (C3Decorator decorator : getDecorators()){
                if(clazz.isAssignableFrom(decorator.getClass())){
                    @SuppressWarnings("unchecked")
                    T castDecorator = (T) decorator; // needed for suppressing the unchecked cast warning
                    ret = castDecorator;
                    break;
                }
            }
        }
        return ret;
    }

    /**
     * Gets all {@link C3Decorator}s for this type
     *
     * @return all {@link C3Decorator}s for this type
     */
    List<C3Decorator> getDecorators();

}
