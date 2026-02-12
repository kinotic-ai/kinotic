

package org.kinotic.rpc.api.annotations;

import java.lang.annotation.*;

/**
 * Container annotation for {@link Alias} annotations
 *
 * Created by navid on 2019-06-01.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Aliases {

    Alias[] value();

}
