package org.kinotic.sql.domain;

/**
 * A field of the documents a statement reads.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 *
 * @param path the field's name, or the dot-joined path to a sub-field of an object field
 */
public record FieldReference(String path) implements SelectExpression {
}
