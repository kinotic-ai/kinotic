package org.kinotic.core.internal.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.internal.api.support.RpcTestService;
import org.kinotic.idl.api.directory.SchemaService;
import org.kinotic.idl.api.directory.SchemaServiceFactory;
import org.kinotic.idl.api.directory.ServiceDeclaration;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.AsyncC3Type;
import org.kinotic.idl.api.schema.ByteC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.StringC3Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

/**
 * Verifies contract conversion against the full kinotic context, where adapters registered at runtime
 * (the Vert.x Future adapter DefaultKinotic adds) must be visible to the {@link SchemaService}.
 * Created by Navíd Mitchell 🤪 on 7/22/26.
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class SchemaServiceTests {

    @Autowired
    private SchemaServiceFactory schemaServiceFactory;

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

    @Test
    public void participantParametersAreLeftOutOfTheContract() {
        Assertions.assertEquals(List.of("prefix", "suffix"),
                                findFunction("middleArgParticipant").getParameters()
                                                                    .stream()
                                                                    .map(ParameterDefinition::getName)
                                                                    .toList());
        Assertions.assertTrue(findFunction("narrowParticipant").getParameters().isEmpty());
    }

    private FunctionDefinition findFunction(String name) {
        SchemaService schemaService = schemaServiceFactory.create(Set.of(Participant.class));
        NamespaceDefinition namespace = schemaService.createForServices(List.of(new ServiceDeclaration(RpcTestService.class, RpcTestService.class)));

        // every type the RPC layer supports must convert, or the service is rejected
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
