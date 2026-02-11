package org.kinotic.continuum.idl.api.schema.decorators;

/**
 * Created by Navíd Mitchell 🤪 on 4/23/23.
 */
public enum DecoratorTarget {

    /** Class, interface, or enum declaration */
    TYPE,

    /** Field declaration (includes enum constants) */
    FIELD,

    /** Function declaration */
    FUNCTION,

    /** Formal parameter declaration */
    PARAMETER,

    /** Constructor declaration */
    CONSTRUCTOR,

    /** Namespace declaration */
    NAMESPACE

}
