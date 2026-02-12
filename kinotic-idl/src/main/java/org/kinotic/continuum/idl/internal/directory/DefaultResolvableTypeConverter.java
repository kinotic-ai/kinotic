

package org.kinotic.continuum.idl.internal.directory;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default converter which can be wired by spring. This is only expected to be used by implementors.
 * <p>
 * Created by navid on 2019-06-13.
 */
@Primary
@Component
public class DefaultResolvableTypeConverter extends ResolvableTypeConverterComposite {

    public DefaultResolvableTypeConverter(List<ResolvableTypeConverter> autowiredConverters) {
        addConverters(autowiredConverters);

        // This is added manually since we want it to always be used last and @Order annotation was not working properly
        addConverter(new PojoTypeConverter());
    }

}
