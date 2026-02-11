package org.kinotic.continuum.idl.api.schema.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Decorators provide a way to add both annotations and a meta-programming syntax for class declarations and members.
 * The {@link C3Decorator} provides a way to define the available decorators, as well as the data needed for each.
 * Created by Navíd Mitchell 🤪 on 4/23/23.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NotNullC3Decorator.class, name = NotNullC3Decorator.type),
})
@JsonInclude(JsonInclude.Include.NON_EMPTY) // do not include any empty or null fields
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode
public abstract class C3Decorator {

    /**
     * The type of the decorator
     * NOTE: This must be set by implementations. It is not declared final for deserialization purposes.
     */
    @JsonIgnore
    protected String type;

    /**
     * Targets specify where the decorator can be applied
     * NOTE: This must be set by implementations. It is not declared final for deserialization purposes.
     */
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    protected List<DecoratorTarget> targets;

}
