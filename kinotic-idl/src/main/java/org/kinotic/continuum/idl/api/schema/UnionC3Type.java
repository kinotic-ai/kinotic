package org.kinotic.continuum.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a union type in the IDL.
 * Union types are a way to represent a type that can be one of many types.
 * Created by Navíd Mitchell 🤪 on 4/13/23.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UnionC3Type extends ComplexC3Type {

    /**
     * The types that are part of this union
     */
    private List<ObjectC3Type> types = new ArrayList<>();

    /**
     * Adds a {@link C3Decorator} to this type
     *
     * @param decorator to add
     */
    @Override
    public UnionC3Type addDecorator(C3Decorator decorator) {
        super.addDecorator(decorator);
        return this;
    }

    /**
     * Sets the name of this {@link UnionC3Type}s
     * @param name of this {@link UnionC3Type}
     * @return this {@link UnionC3Type} for chaining
     */
    public UnionC3Type setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the namespace of this {@link UnionC3Type}s
     * @param namespace of this {@link UnionC3Type}
     * @return this {@link UnionC3Type} for chaining
     */
    public UnionC3Type setNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

}
