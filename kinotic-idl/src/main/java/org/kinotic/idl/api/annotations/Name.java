

package org.kinotic.idl.api.annotations;

import java.lang.annotation.*;

/**
 * {@link Name} can be used to provide a name for a method parameter that can be used at runtime.
 * Created by Navid Mitchell on 2019-01-18.
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Name {

    /**
     * The value is the logical name that will be used
     */
    String value();

}
