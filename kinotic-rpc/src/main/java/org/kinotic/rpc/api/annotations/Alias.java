

package org.kinotic.rpc.api.annotations;

import java.lang.annotation.*;

/**
 * Allows for an Alias to be defined for a {@link Publish}ed class or methods.
 * This is mostly used for accessing services via a command line or other human interfaces.
 *
 *
 * Created by navid on 2019-06-01.
 */
@Repeatable(Aliases.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Alias {

    /**
     * The value is the logical name that can be used to reference this {@link Alias}
     */
    String value();

}
