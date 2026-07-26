package org.kinotic.vertx.jackson3;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

public class TestMapperCustomizer implements Jackson3MapperCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.addModule(markerModule("customized"));
    }

    static SimpleModule markerModule(String marker) {
        SimpleModule module = new SimpleModule("marker-" + marker);
        module.addSerializer(TestCustomType.class, new StdSerializer<>(TestCustomType.class) {
            @Override
            public void serialize(TestCustomType value, JsonGenerator jgen, SerializationContext provider) {
                jgen.writeString(marker);
            }
        });
        return module;
    }
}
