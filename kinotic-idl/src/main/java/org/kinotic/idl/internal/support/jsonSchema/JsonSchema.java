

package org.kinotic.idl.internal.support.jsonSchema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Optional;

/**
 * Java implementation of Json schema spec with small changes. Any changes to the original will be denoted here.
 * <p>
 * https://json-schema.org/understanding-json-schema/reference/index.html
 * <p>
 * Changes From the Spec:
 * <p>
 *  - Object type:
 *      Additional properties flag will always be false.
 *      Property Dependencies are NOT supported ( https://json-schema.org/understanding-json-schema/reference/object.html#dependenices )
 *      Pattern Properties are NOT supported ( https://json-schema.org/understanding-json-schema/reference/object.html#patternproperties )
 * <p>
 *  - Array Type:
 *      Additional items flag will always be false.
 * <p>
 *  - Generic Keywords:
 *      Currently none of the generic keywords are supported https://json-schema.org/understanding-json-schema/reference/generic.html
 *
 *
 * Created by navid on 2019-06-11.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringJsonSchema.class, name = "string"),
        @JsonSubTypes.Type(value = NumberJsonSchema.class, name = "number"),
        @JsonSubTypes.Type(value = ObjectJsonSchema.class, name = "object"),
        @JsonSubTypes.Type(value = ArrayJsonSchema.class, name = "array"),
        @JsonSubTypes.Type(value = MapJsonSchema.class, name = "map"),
        @JsonSubTypes.Type(value = BooleanJsonSchema.class, name = "boolean"),
        @JsonSubTypes.Type(value = NullJsonSchema.class, name = "null")
})
@JsonInclude(JsonInclude.Include.NON_EMPTY) // do not include any empty or null fields
public abstract class JsonSchema {

    /**
     * The title and description keywords must be strings.
     * A “title” will preferably be short, whereas a “description” will provide a more lengthy explanation about the purpose of the data described by the schema.
     */
    private String title = null;
    private String description = null;


    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public JsonSchema setTitle(String title) {
        this.title = title;
        return this;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public JsonSchema setDescription(String description) {
        this.description = description;
        return this;
    }
}
