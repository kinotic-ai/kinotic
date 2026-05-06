package org.kinotic.test.tests.sql;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.Column;
import org.kinotic.sql.domain.ColumnType;
import org.kinotic.sql.executor.TypeMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for TypeMapper — no Spring, no Elasticsearch.
 * Builds Column trees manually and asserts on the resulting Property objects.
 */
class TypeMapperTest {

    // ── OBJECT ───────────────────────────────────────────────────────────────

    @Test
    void whenMapObjectColumn_thenObjectPropertyWithSubFields() {
        Column col = new Column("address", ColumnType.OBJECT, true, List.of(
            new Column("street", ColumnType.TEXT),
            new Column("city", ColumnType.KEYWORD)
        ));
        Property prop = TypeMapper.mapType(col);

        assertEquals(Property.Kind.Object, prop._kind());
        Map<String, Property> sub = prop.object().properties();
        assertEquals(Property.Kind.Text, sub.get("street")._kind());
        assertEquals(Property.Kind.Keyword, sub.get("city")._kind());
    }

    @Test
    void whenMapObjectColumn_thenDynamicStrictEnforced() {
        Column col = new Column("address", ColumnType.OBJECT, true, List.of(
            new Column("street", ColumnType.TEXT)
        ));
        Property prop = TypeMapper.mapType(col);

        assertEquals(DynamicMapping.Strict, prop.object().dynamic());
    }

    @Test
    void whenMapObjectNotIndexed_thenObjectDisabled() {
        Column col = new Column("payload", ColumnType.OBJECT, false, List.of(
            new Column("raw", ColumnType.TEXT)
        ));
        Property prop = TypeMapper.mapType(col);

        assertEquals(Property.Kind.Object, prop._kind());
        assertFalse(prop.object().enabled());
    }

    // ── NESTED ───────────────────────────────────────────────────────────────

    @Test
    void whenMapNestedColumn_thenNestedPropertyWithSubFields() {
        Column col = new Column("tags", ColumnType.NESTED, true, List.of(
            new Column("label", ColumnType.TEXT),
            new Column("value", ColumnType.KEYWORD)
        ));
        Property prop = TypeMapper.mapType(col);

        assertEquals(Property.Kind.Nested, prop._kind());
        Map<String, Property> sub = prop.nested().properties();
        assertEquals(Property.Kind.Text, sub.get("label")._kind());
        assertEquals(Property.Kind.Keyword, sub.get("value")._kind());
    }

    @Test
    void whenMapNestedColumn_thenDynamicStrictEnforced() {
        Column col = new Column("tags", ColumnType.NESTED, true, List.of(
            new Column("label", ColumnType.TEXT)
        ));
        Property prop = TypeMapper.mapType(col);

        assertEquals(DynamicMapping.Strict, prop.nested().dynamic());
    }

    // ── UNION ────────────────────────────────────────────────────────────────

    @Test
    void whenMapUnionColumn_thenMergedFlatObject() {
        Column col = new Column("item", ColumnType.UNION, true, List.of(
            new Column("Book", ColumnType.OBJECT, true, List.of(
                new Column("kind", ColumnType.KEYWORD),
                new Column("title", ColumnType.TEXT),
                new Column("isbn", ColumnType.KEYWORD)
            )),
            new Column("Video", ColumnType.OBJECT, true, List.of(
                new Column("kind", ColumnType.KEYWORD),
                new Column("title", ColumnType.TEXT),
                new Column("duration", ColumnType.INTEGER)
            ))
        ));
        Property prop = TypeMapper.mapType(col);

        assertEquals(Property.Kind.Object, prop._kind());
        Map<String, Property> sub = prop.object().properties();
        assertEquals(Property.Kind.Keyword, sub.get("kind")._kind());
        assertEquals(Property.Kind.Text, sub.get("title")._kind());
        assertEquals(Property.Kind.Keyword, sub.get("isbn")._kind());
        assertEquals(Property.Kind.Integer, sub.get("duration")._kind());
    }

    @Test
    void whenMapUnionNotIndexed_thenObjectDisabled() {
        Column col = new Column("item", ColumnType.UNION, false, List.of(
            new Column("TypeA", ColumnType.OBJECT, true, List.of(
                new Column("x", ColumnType.TEXT)
            ))
        ));
        Property prop = TypeMapper.mapType(col);

        assertEquals(Property.Kind.Object, prop._kind());
        assertFalse(prop.object().enabled());
    }

    @Test
    void whenMapUnionColumn_thenDynamicStrictEnforced() {
        Column col = new Column("item", ColumnType.UNION, true, List.of(
            new Column("TypeA", ColumnType.OBJECT, true, List.of(
                new Column("x", ColumnType.TEXT)
            ))
        ));
        Property prop = TypeMapper.mapType(col);

        assertEquals(DynamicMapping.Strict, prop.object().dynamic());
    }

    // ── RECURSION ────────────────────────────────────────────────────────────

    @Test
    void whenMapObjectInsideObject_thenRecursiveMappingCorrect() {
        Column inner = new Column("dims", ColumnType.OBJECT, true, List.of(
            new Column("w", ColumnType.FLOAT),
            new Column("h", ColumnType.FLOAT)
        ));
        Column outer = new Column("box", ColumnType.OBJECT, true, List.of(
            new Column("label", ColumnType.TEXT),
            inner
        ));
        Property prop = TypeMapper.mapType(outer);

        assertEquals(Property.Kind.Object, prop._kind());
        Property dimsProperty = prop.object().properties().get("dims");
        assertEquals(Property.Kind.Object, dimsProperty._kind());
        assertEquals(Property.Kind.Float, dimsProperty.object().properties().get("w")._kind());
        assertEquals(Property.Kind.Float, dimsProperty.object().properties().get("h")._kind());
    }

    @Test
    void whenMapUnionInsideObject_thenUnionMergedCorrectly() {
        Column union = new Column("item", ColumnType.UNION, true, List.of(
            new Column("TypeA", ColumnType.OBJECT, true, List.of(new Column("x", ColumnType.TEXT))),
            new Column("TypeB", ColumnType.OBJECT, true, List.of(new Column("y", ColumnType.INTEGER)))
        ));
        Column outer = new Column("container", ColumnType.OBJECT, true, List.of(
            new Column("id", ColumnType.KEYWORD),
            union
        ));
        Property prop = TypeMapper.mapType(outer);

        assertEquals(Property.Kind.Object, prop._kind());
        Property itemProp = prop.object().properties().get("item");
        assertEquals(Property.Kind.Object, itemProp._kind());
        // Both variant fields present in the merged object
        assertTrue(itemProp.object().properties().containsKey("x"));
        assertTrue(itemProp.object().properties().containsKey("y"));
    }

    @Test
    void whenMapNestedInsideObject_thenNestedPropertyMappedCorrectly() {
        Column nested = new Column("tags", ColumnType.NESTED, true, List.of(
            new Column("label", ColumnType.TEXT)
        ));
        Column outer = new Column("doc", ColumnType.OBJECT, true, List.of(
            new Column("title", ColumnType.TEXT),
            nested
        ));
        Property prop = TypeMapper.mapType(outer);

        assertEquals(Property.Kind.Object, prop._kind());
        assertEquals(Property.Kind.Nested, prop.object().properties().get("tags")._kind());
    }

    // ── KNOWN LIMITATION ─────────────────────────────────────────────────────

    @Test
    void whenUnionVariantsShareObjectFieldWithDifferentSubFields_thenFirstVariantWins() {
        // Two variants both declare `specs OBJECT` with different sub-fields.
        // Conflict detection is shallow: same ColumnType (OBJECT) means no exception.
        // The first variant's sub-fields are used; the second's are silently dropped.
        Column col = new Column("item", ColumnType.UNION, true, List.of(
            new Column("Car", ColumnType.OBJECT, true, List.of(
                new Column("kind", ColumnType.KEYWORD),
                new Column("specs", ColumnType.OBJECT, true, List.of(
                    new Column("doors", ColumnType.INTEGER),
                    new Column("horsepower", ColumnType.INTEGER)
                ))
            )),
            new Column("Truck", ColumnType.OBJECT, true, List.of(
                new Column("kind", ColumnType.KEYWORD),
                new Column("specs", ColumnType.OBJECT, true, List.of(
                    new Column("payload", ColumnType.FLOAT),
                    new Column("axles", ColumnType.INTEGER)
                ))
            ))
        ));

        // No exception thrown — shallow check passes
        Property prop = assertDoesNotThrow(() -> TypeMapper.mapType(col));

        Map<String, Property> specsProps = prop.object().properties().get("specs").object().properties();
        // Car's sub-fields are present
        assertTrue(specsProps.containsKey("doors"));
        assertTrue(specsProps.containsKey("horsepower"));
        // Truck's sub-fields are silently dropped
        assertFalse(specsProps.containsKey("payload"));
        assertFalse(specsProps.containsKey("axles"));
    }
}
