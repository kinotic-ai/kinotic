package org.kinotic.idl.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.schema.FunctionDefinition;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

public class TestFunctionDefinitionSerialization {

    @Test
    public void emptyParametersSurviveRoundTripWithDetectionDisabled() {
        // the platform mapper policy: -parameters makes @AllArgsConstructor names visible, and with
        // detection on Jackson promotes that constructor to a properties-based creator, passing null
        // for the NON_EMPTY-omitted parameters list instead of running the field initializer
        JsonMapper mapper = JsonMapper.builder()
                                      .disable(MapperFeature.DETECT_PARAMETER_NAMES)
                                      .build();
        String json = mapper.writeValueAsString(new FunctionDefinition().setName("f"));
        Assertions.assertFalse(json.contains("parameters"), "NON_EMPTY should omit the empty list, got: " + json);
        FunctionDefinition read = mapper.readValue(json, FunctionDefinition.class);
        Assertions.assertNotNull(read.getParameters(), "parameters must survive a round trip");
    }

    @Test
    public void defaultDetectionNullsOmittedCreatorProperties() {
        // documents the hazard the policy exists for; if a Jackson upgrade makes this fail, the
        // DETECT_PARAMETER_NAMES policy can be revisited
        JsonMapper mapper = JsonMapper.builder().build();
        FunctionDefinition read = mapper.readValue(
                mapper.writeValueAsString(new FunctionDefinition().setName("f")), FunctionDefinition.class);
        Assertions.assertNull(read.getParameters());
    }
}
