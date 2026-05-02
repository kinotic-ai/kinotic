package org.kinotic.sql.executor;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import org.kinotic.sql.domain.Column;
import org.kinotic.sql.domain.ColumnType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for mapping SQL types to Elasticsearch field types.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class TypeMapper {

    public static Property mapType(Column column) {
        boolean indexed = column.indexed();
        return switch (column.type()) {
            case TEXT -> Property.of(p -> p.text(t -> t));
            case KEYWORD, UUID -> Property.of(p -> p.keyword(k -> indexed ? k : k.index(false).docValues(false)));
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
            case DECIMAL -> Property.of(p -> p.scaledFloat(sf -> indexed ? sf : sf.index(false).docValues(false)));
            case OBJECT -> buildObjectProperty(column);
            case NESTED -> buildNestedProperty(column);
            case UNION -> buildUnionProperty(column);
        };
    }

    private static Property buildObjectProperty(Column column) {
        if (!column.indexed()) {
            return Property.of(p -> p.object(o -> o.enabled(false)));
        }
        Map<String, Property> subProps = buildSubProperties(column.subColumns());
        return Property.of(p -> p.object(o -> o.dynamic(DynamicMapping.Strict).properties(subProps)));
    }

    private static Property buildNestedProperty(Column column) {
        Map<String, Property> subProps = buildSubProperties(column.subColumns());
        return Property.of(p -> p.nested(n -> n.dynamic(DynamicMapping.Strict).properties(subProps)));
    }

    private static Property buildUnionProperty(Column column) {
        if (!column.indexed()) {
            return Property.of(p -> p.object(o -> o.enabled(false)));
        }
        // Merge all variant fields into one flat object; throw on same-name/different-type or same-name/different-indexed conflict.
        Map<String, Column> merged = new LinkedHashMap<>();
        for (Column variant : column.subColumns()) {
            for (Column field : variant.subColumns()) {
                Column existing = merged.get(field.name());
                if (existing != null && (existing.type() != field.type() || existing.indexed() != field.indexed())) {
                    String detail = existing.type() != field.type()
                        ? "conflicting types: " + existing.type() + " vs " + field.type()
                        : "conflicting indexed flags: indexed=" + existing.indexed() + " vs indexed=" + field.indexed();
                    throw new IllegalArgumentException(
                        "Field '" + field.name() + "' in UNION column '" + column.name() + "' has " + detail);
                }
                merged.putIfAbsent(field.name(), field);
            }
        }
        Map<String, Property> subProps = buildSubProperties(List.copyOf(merged.values()));
        return Property.of(p -> p.object(o -> o.dynamic(DynamicMapping.Strict).properties(subProps)));
    }

    private static Map<String, Property> buildSubProperties(List<Column> columns) {
        Map<String, Property> props = new LinkedHashMap<>();
        for (Column col : columns) {
            props.put(col.name(), mapType(col));
        }
        return props;
    }
}
