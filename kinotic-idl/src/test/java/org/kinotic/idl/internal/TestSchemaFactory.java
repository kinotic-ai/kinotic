package org.kinotic.idl.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.directory.ServiceSchema;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.internal.support.TestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * Created by Navíd Mitchell 🤪 on 4/14/23.
 */
@SpringBootTest
@ActiveProfiles("test")
public class TestSchemaFactory {

    private static final Logger log = LoggerFactory.getLogger(TestSchemaFactory.class);

    @Autowired
    private SchemaFactory schemaFactory;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    public void testSchemaFactory() throws Exception {
        ServiceSchema serviceSchema = schemaFactory.createForService(TestService.class);

        ServiceDefinition serviceDefinition = serviceSchema.serviceDefinition();

        Assertions.assertEquals(TestService.class.getName(), serviceDefinition.getQualifiedName());

        Assertions.assertEquals(3, serviceDefinition.getFunctions().size());

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(serviceSchema);
        log.info("Service Schema\n"+json);
    }

}
