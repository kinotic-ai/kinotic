package org.kinotic.auth.engines;

import org.kinotic.auth.api.engine.AuthorizationRequest;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the raw JSON carried by an {@link AuthorizationRequest} into the maps the candidate
 * engines evaluate against. Integers become {@link Long} so every engine sees one numeric type.
 */
public final class RequestJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_LONG_FOR_INTS)
            .build();

    private RequestJson() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> subject(AuthorizationRequest request) {
        String json = request.principalAttributesJson();
        Map<String, Object> ret;
        if (json == null || json.isBlank()) {
            ret = Map.of();
        } else {
            ret = MAPPER.readValue(json, Map.class);
        }
        return ret;
    }

    /**
     * Maps the positional argument array onto the parameter names, e.g. {@code [{"amount":1}]} with
     * names {@code ["order"]} becomes {@code {"order": {"amount": 1}}}.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> arguments(AuthorizationRequest request) {
        List<Object> arguments = MAPPER.readValue(request.argumentsJson(), List.class);
        List<String> names = request.parameterNames();
        Map<String, Object> named = new HashMap<>();
        for (int i = 0; i < arguments.size() && i < names.size(); i++) {
            named.put(names.get(i), arguments.get(i));
        }
        return named;
    }
}
