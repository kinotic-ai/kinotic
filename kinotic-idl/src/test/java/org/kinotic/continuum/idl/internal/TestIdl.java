package org.kinotic.continuum.idl.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.continuum.idl.api.schema.ArrayC3Type;
import org.kinotic.continuum.idl.api.schema.ObjectC3Type;
import org.kinotic.continuum.idl.api.schema.StringC3Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * Created by Navíd Mitchell 🤪on 6/19/23.
 */
@SpringBootTest
@ActiveProfiles("test")
public class TestIdl {


    @Autowired
    private JsonMapper jsonMapper;

    @Test
    public void testSerializeDeserializeObjectC3Type() throws Exception{
        ObjectC3Type objectC3Type =  new ObjectC3Type()
                .setName("Person")
                .setNamespace("org.kinotic.sample")
                .addProperty("id", new StringC3Type())
                .addProperty("firstName", new StringC3Type())
                .addProperty("lastName", new StringC3Type())
                .addProperty("addresses", new ArrayC3Type()
                        .setContains(new ObjectC3Type()
                                             .setName("Address")
                                             .setNamespace("org.kinotic.sample")
                                             .addProperty("street", new StringC3Type())
                                             .addProperty("city", new StringC3Type())
                                             .addProperty("state", new StringC3Type())
                                             .addProperty("zip", new StringC3Type())));

        String json = jsonMapper.writeValueAsString(objectC3Type);

        ObjectC3Type deserialized = jsonMapper.readValue(json, ObjectC3Type.class);

        Assertions.assertEquals(objectC3Type, deserialized);
        Assertions.assertEquals(objectC3Type.getProperties(), deserialized.getProperties());

    }
}
