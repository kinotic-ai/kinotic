package org.kinotic.core.internal.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.internal.api.support.RpcTestService;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.schema.AsyncC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.StringC3Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * Verifies contract capture against the full kinotic context, where adapters registered at runtime
 * (the Vert.x Future adapter DefaultKinotic adds) must be visible to the {@link SchemaFactory}.
 * Created by Navíd Mitchell 🤪 on 7/22/26.
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class SchemaFactoryTests {

    @Autowired
    private SchemaFactory schemaFactory;

    @Test
    public void vertxFutureConvertsToAsyncC3Type() {
        NamespaceDefinition namespace = schemaFactory.createForServices(List.of(RpcTestService.class));

        // every return type the RPC layer supports must convert, or the whole service is omitted
        ServiceDefinition service = namespace.getServices()
                                             .stream()
                                             .findFirst()
                                             .orElseThrow(() -> new AssertionError("RpcTestService failed to convert"));

        FunctionDefinition function = service.getFunctions()
                                             .stream()
                                             .filter(f -> f.getName().equals("getVertxFutureNullString"))
                                             .findFirst()
                                             .orElseThrow();

        Assertions.assertEquals(new AsyncC3Type(new StringC3Type()), function.getReturnType());
    }

}
