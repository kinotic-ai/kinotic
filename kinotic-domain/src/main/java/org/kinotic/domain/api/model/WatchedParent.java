package org.kinotic.domain.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.Validate;

/**
 * The record that made a {@link Watched} record and unmakes it: told when the record changes, and
 * taking the record along when it is deleted. Serialized as one string, {@code TYPE:id}, so a
 * store holds it as a single keyword.
 *
 * @param type the parent's kind
 * @param id   the parent's id
 */
public record WatchedParent(WatchedType type, String id) {

    public WatchedParent {
        Validate.notNull(type, "type cannot be null");
        Validate.notBlank(id, "id cannot be blank");
    }

    /**
     * @param value a parent as {@link #value()} renders it
     * @return the parent the value names
     */
    @JsonCreator
    public static WatchedParent parse(String value) {
        Validate.notBlank(value, "value cannot be blank");
        // the type never holds a colon, so the first one is the separator whatever the id holds
        int separator = value.indexOf(':');
        Validate.isTrue(separator > 0, "not a watched parent: %s", value);
        return new WatchedParent(WatchedType.valueOf(value.substring(0, separator)), value.substring(separator + 1));
    }

    /**
     * @return the parent as one string, {@code TYPE:id}
     */
    @JsonValue
    public String value() {
        return type + ":" + id;
    }
}
