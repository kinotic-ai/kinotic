package org.kinotic.continuum.api.annotations;

import java.lang.annotation.*;

/**
 * Specifies the version of a {@link Publish}ed service or {@link Proxy} interface.
 * Created by Navíd Mitchell 🤪 on 2/28/23.
 */
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Version {

    /**
     * The semantic version of the published interface. This is required.
     * Examples: 1.0.0, 1.0.0-SNAPSHOT, 1.0.0-RC1
     */
    String value();

}
