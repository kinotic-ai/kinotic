package org.kinotic.continuum.idl.api.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Created by Navíd Mitchell 🤪 on 4/20/24.
 */
public interface HasQualifiedName {

    /**
     * This is the namespace of this such as "org.kinotic.continuum.idl.api.schema"
     * @return the namespace of this
     */
    String getNamespace();

    /**
     * This is the name of this such as "Person", "Animal"
     * @return the name of this
     */
    String getName();


    /**
     * The fully qualified name of this such as "org.kinotic.continuum.idl.api.schema.Person"
     * @return the fully qualified name of this
     */
    @JsonIgnore
    default String getQualifiedName(){
        return getNamespace() + "." + getName();
    }

}
