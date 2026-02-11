package org.kinotic.continuum.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 4/13/23.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EnumC3Type extends ComplexC3Type {

    /**
     * The values that are part of this enum
     */
    private List<String> values = new ArrayList<>();

    /**
     * Adds a value to this enum
     * @param value to add
     * @return this for chaining
     */
    public EnumC3Type addValue(String value){
        values.add(value);
        return this;
    }

    /**
     * Adds a {@link C3Decorator} to this type
     *
     * @param decorator to add
     */
    @Override
    public EnumC3Type addDecorator(C3Decorator decorator) {
        super.addDecorator(decorator);
        return this;
    }

    /**
     * Sets the name of this {@link EnumC3Type}s
     * @param name of this {@link EnumC3Type}
     * @return this {@link EnumC3Type} for chaining
     */
    public EnumC3Type setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the namespace of this {@link EnumC3Type}s
     * @param namespace of this {@link EnumC3Type}
     * @return this {@link EnumC3Type} for chaining
     */
    public EnumC3Type setNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }
}
