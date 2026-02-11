package org.kinotic.continuum.idl.api.schema;

import java.util.Map;

/**
 * Defines a type that can have metadata
 * Created by Navíd Mitchell 🤪 on 4/19/24.
 */
public interface HasMetadata {

    /**
     * The metadata keyword is legal on any {@link AbstractDefinition}, The objects provided must be serializable to JSON.
     * Usually, metadata is for putting things like descriptions or hints for code generators, or other things tools can use.
     */
    Map<String, ?> getMetadata();

}
