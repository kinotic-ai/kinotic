package org.kinotic.domain.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.Validate;

/**
 * The record that made a {@link Watched} record and unmakes it: told when the record changes, and
 * taking the record along when it is deleted. Serialized as one string, {@code TYPE:scope:id}, so
 * a store holds it as a single keyword.
 *
 * @param type  the parent's kind
 * @param scope the scope the parent is stored under, as its repository's {@code scopeOf} gives it
 * @param id    the parent's id
 */
public record WatchedParent(WatchedType type, String scope, String id) {

    public WatchedParent {
        Validate.notNull(type, "type cannot be null");
        Validate.notBlank(scope, "scope cannot be blank");
        Validate.notBlank(id, "id cannot be blank");
    }

    /**
     * @param value a parent as {@link #value()} renders it
     * @return the parent the value names
     */
    @JsonCreator
    public static WatchedParent parse(String value) {
        Validate.notBlank(value, "value cannot be blank");
        // neither the type nor the scope holds a colon, so the first two are the separators whatever
        // the id holds
        int first = value.indexOf(':');
        int second = first < 0 ? -1 : value.indexOf(':', first + 1);
        Validate.isTrue(first > 0 && second > first + 1, "not a watched parent: %s", value);
        return new WatchedParent(WatchedType.valueOf(value.substring(0, first)),
                                 value.substring(first + 1, second),
                                 value.substring(second + 1));
    }

    /**
     * @return the parent as one string, {@code TYPE:scope:id}
     */
    @JsonValue
    public String value() {
        return type + ":" + scope + ":" + id;
    }
}
