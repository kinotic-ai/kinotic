package org.kinotic.core.internal.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.internal.api.support.RpcTestService;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.AsyncC3Type;
import org.kinotic.idl.api.schema.ByteC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.StringC3Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * Verifies contract conversion against the full kinotic context, where adapters registered at runtime
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
        Assertions.assertEquals(new AsyncC3Type(new StringC3Type()),
                                findFunction("getVertxFutureNullString").getReturnType());
    }

    @Test
    public void bufferConvertsToByteArray() {
        Assertions.assertEquals(new ArrayC3Type(new ByteC3Type()),
                                findFunction("getBuffer").getReturnType());
    }

    @Test
    public void tokenBufferConvertsToAny() {
        Assertions.assertEquals(new AnyC3Type(),
                                findFunction("echoTokenBuffer").getParameters().getFirst().getType());
    }

    private FunctionDefinition findFunction(String name) {
        NamespaceDefinition namespace = schemaFactory.createForServices(List.of(RpcTestService.class));

        // every type the RPC layer supports must convert, or the whole service is omitted
        ServiceDefinition service = namespace.getServices()
                                             .stream()
                                             .findFirst()
                                             .orElseThrow(() -> new AssertionError("RpcTestService failed to convert"));

        return service.getFunctions()
                      .stream()
                      .filter(f -> f.getName().equals(name))
                      .findFirst()
                      .orElseThrow();
    }

}
