package org.kinotic.continuum.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Navíd Mitchell 🤪 on 4/20/24.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public abstract class ComplexC3Type extends C3Type implements HasQualifiedName, HasDecorators, HasMetadata {

    /**
     * This is the namespace of the {@link ComplexC3Type} such as "org.kinotic.continuum.idl.api.schema"
     */
    protected String namespace = null;

    /**
     * This is the name of the {@link ComplexC3Type} such as "Person", "Animal"
     */
    protected String name = null;

    /**
     * The list of {@link C3Decorator}s that should be applied to this type
     */
    @EqualsAndHashCode.Exclude
    protected List<C3Decorator> decorators = null;

    /**
     * The metadata keyword is legal on any {@link ComplexC3Type}, The objects provided must be serializable to JSON.
     * Usually,s metadata is for putting things like descriptions or hints for code generators, or other things tools can use.
     */
    @EqualsAndHashCode.Exclude
    protected Map<String, ?> metadata = null;

    /**
     * Adds a {@link C3Decorator} to this type
     *
     * @param decorator to add
     */
    public ComplexC3Type addDecorator(C3Decorator decorator){
        Validate.notNull(decorator, "decorator cannot be null");
        if(getDecorators() == null){
            decorators = new ArrayList<>();
        }else{
            Validate.isTrue(!getDecorators().contains(decorator), "C3Base already contains decorator "+decorator);
        }
        decorators.add(decorator);
        return this;
    }

}
