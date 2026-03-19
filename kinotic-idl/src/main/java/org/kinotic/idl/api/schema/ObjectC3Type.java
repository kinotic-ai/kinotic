

package org.kinotic.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.LinkedList;
import java.util.List;

/**
 * {@link ObjectC3Type} is used to define a complex object type in the Continuum IDL.
 * Properties are defined with {@link PropertyDefinition}s
 * The context for equality here is the name.
 * Given no two object types can have the same namespace and name this is the only context needed for equality.
 * Created by navid on 2019-06-11.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ObjectC3Type extends ComplexC3Type {

    /**
     * The parent schema of this object definition
     * This is used to support inheritance
     */
    @EqualsAndHashCode.Exclude
    private ObjectC3Type parent = null;

    /**
     * The properties are the fields of this object type.
     */
    @EqualsAndHashCode.Exclude
    @JsonDeserialize(as = LinkedList.class)
    private LinkedList<PropertyDefinition> properties = new LinkedList<>();

    /**
     * Adds a {@link C3Decorator} to this type
     *
     * @param decorator to add
     */
    @Override
    public ObjectC3Type addDecorator(C3Decorator decorator) {
        super.addDecorator(decorator);
        return this;
    }

    /**
     * Adds a property to this {@link ObjectC3Type}
     * @param name of the property
     * @param c3Type the type of the property
     * @return this {@link ObjectC3Type} for chaining
     */
    public ObjectC3Type addProperty(String name, C3Type c3Type){
        PropertyDefinition property = new PropertyDefinition(name, c3Type);
        return addProperty(property);
    }

    /**
     * Adds a property to this {@link ObjectC3Type}
     * @param name of the property
     * @param c3Type the type of the property]
     * @param decorators the decorators to apply to the property
     * @return this {@link ObjectC3Type} for chaining
     */
    public ObjectC3Type addProperty(String name, C3Type c3Type, List<C3Decorator> decorators){
        PropertyDefinition property = new PropertyDefinition(name, c3Type);
        property.setDecorators(decorators);
        return addProperty(property);
    }

    /**
     * Adds a property to this {@link ObjectC3Type}
     * @param propertyDefinition to add to this {@link ObjectC3Type}
     * @return this {@link ObjectC3Type} for chaising
     */
    public ObjectC3Type addProperty(PropertyDefinition propertyDefinition){
        Validate.isTrue(!properties.contains(propertyDefinition), "ObjectC3Type already contains property "+propertyDefinition.getName());
        properties.add(propertyDefinition);
        return this;
    }

    /**
     * Sets the name of this {@link ObjectC3Type}s
     * @param name of this {@link ObjectC3Type}
     * @return this {@link ObjectC3Type} for chaining
     */
    public ObjectC3Type setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the namespace of this {@link ObjectC3Type}s
     * @param namespace of this {@link ObjectC3Type}
     * @return this {@link ObjectC3Type} for chaining
     */
    public ObjectC3Type setNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

}
