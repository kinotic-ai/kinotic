package org.mindignited.structures.sql.executor;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import org.mindignited.structures.sql.domain.Column;
import org.mindignited.structures.sql.domain.ColumnType;

/**
 * Utility class for mapping SQL types to Elasticsearch field types.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class TypeMapper {

    public static Property mapType(Column column) {
        return mapType(column.type(), column.indexed());
    }

    private static Property mapType(ColumnType type, boolean indexed) {
        return switch (type) {
            case TEXT -> Property.of(p -> p.text(t -> t));
            case KEYWORD -> Property.of(p -> p.keyword(k -> indexed ? k : k.index(false).docValues(false)));
            case INTEGER -> Property.of(p -> p.integer(i -> indexed ? i : i.index(false).docValues(false)));
            case LONG -> Property.of(p -> p.long_(l -> indexed ? l : l.index(false).docValues(false)));
            case FLOAT -> Property.of(p -> p.float_(f -> indexed ? f : f.index(false).docValues(false)));
            case DOUBLE -> Property.of(p -> p.double_(d -> indexed ? d : d.index(false).docValues(false)));
            case BOOLEAN -> Property.of(p -> p.boolean_(b -> indexed ? b : b.index(false).docValues(false)));
            case DATE -> Property.of(p -> p.date(d -> indexed ? d : d.index(false).docValues(false)));
            case JSON -> Property.of(p -> indexed ? p.flattened(f -> f) : p.object(o -> o.enabled(false)));
            case BINARY -> Property.of(p -> p.binary(b -> b));
            case GEO_POINT -> Property.of(p -> p.geoPoint(gp -> gp));
            case GEO_SHAPE -> Property.of(p -> p.geoShape(gs -> gs));
            case UUID -> Property.of(p -> p.keyword(k -> indexed ? k : k.index(false).docValues(false)));
            case DECIMAL -> Property.of(p -> p.scaledFloat(sf -> indexed ? sf : sf.index(false).docValues(false)));
            default -> throw new IllegalArgumentException("Unsupported SQL type: " + type);
        };
    }
}