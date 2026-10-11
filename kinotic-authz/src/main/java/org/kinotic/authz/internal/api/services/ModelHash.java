package org.kinotic.authz.internal.api.services;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import net.openhft.hashing.LongTupleHashFunction;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

/**
 * The digest that identifies an authorization model's definition, equal for a generated definition and for the
 * same definition read back from a store.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
final class ModelHash {

    // an empty object under these keys is the meaning itself: `this` marks a directly assignable relation and
    // `wildcard` marks a type reference to every object of the type
    private static final Set<String> MEANINGFUL_EMPTY_OBJECTS = Set.of("this", "wildcard");

    private ModelHash() {
    }

    /**
     * The hex digest of the definition's canonical form: its 128-bit xxHash, which equal definitions share and
     * a changed one differs in.
     */
    static String of(JsonNode definition) {
        long[] hash = LongTupleHashFunction.xx128().hashChars(canonical(definition).toString());
        return HexFormat.of().formatHex(ByteBuffer.allocate(Long.BYTES * 2).putLong(hash[0]).putLong(hash[1]).array());
    }

    /**
     * The definition with its keys sorted and every default-valued field left out, which is the form OpenFGA
     * returns a model in: a null, an empty string, an empty array and an empty object are dropped, except an
     * empty object under {@code this} or {@code wildcard}.
     */
    static JsonNode canonical(JsonNode node) {
        JsonNode ret;
        if (node.isObject()) {
            ObjectNode object = JsonNodeFactory.instance.objectNode();
            for (String name : new TreeSet<>(node.propertyNames())) {
                JsonNode value = canonical(node.get(name));
                if (!isDefault(name, value)) {
                    object.set(name, value);
                }
            }
            ret = object;
        } else if (node.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : node) {
                array.add(canonical(child));
            }
            ret = array;
        } else {
            ret = node;
        }
        return ret;
    }

    private static boolean isDefault(String name, JsonNode value) {
        return value.isNull()
                || value.isMissingNode()
                || (value.isString() && value.asString().isEmpty())
                || (value.isArray() && value.isEmpty())
                || (value.isObject() && value.isEmpty() && !MEANINGFUL_EMPTY_OBJECTS.contains(name));
    }

}
