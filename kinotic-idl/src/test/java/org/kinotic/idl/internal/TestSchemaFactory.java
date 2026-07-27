package org.kinotic.idl.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.kinotic.idl.api.schema.AsyncC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.EnumC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.NamespaceDefinition;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.ReferenceC3Type;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.StreamC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.kinotic.idl.internal.support.BrokenTestService;
import org.kinotic.idl.internal.support.DefaultTestRenamedService;
import org.kinotic.idl.internal.support.TestRenamedService;
import org.kinotic.idl.internal.support.OtherTestService;
import org.kinotic.idl.internal.support.TestObject;
import org.kinotic.idl.internal.support.TestObjectCrudService;
import org.kinotic.idl.internal.support.TestService;
import org.kinotic.idl.internal.support.TestStatus;
import org.kinotic.idl.internal.support.TestSweptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

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
        NamespaceDefinition namespaceDefinition = schemaFactory.createForServices(Map.of(TestService.class, TestService.class,
                                                                                         OtherTestService.class, OtherTestService.class));

        Assertions.assertEquals(2, namespaceDefinition.getServices().size());

        ServiceDefinition testService = findService(namespaceDefinition, TestService.class);
        Assertions.assertEquals(4, testService.getFunctions().size());

        ServiceDefinition otherTestService = findService(namespaceDefinition, OtherTestService.class);
        Assertions.assertEquals(5, otherTestService.getFunctions().size());

        // async and streaming returns wrap the same value type the synchronous variant resolves to
        C3Type personType = findFunction(otherTestService, "findPerson").getReturnType();
        Assertions.assertEquals(new AsyncC3Type(personType),
                                findFunction(otherTestService, "findPersonAsync").getReturnType());
        Assertions.assertEquals(new StreamC3Type(personType),
                                findFunction(otherTestService, "streamPeople").getReturnType());
        Assertions.assertEquals(new AsyncC3Type(findFunction(otherTestService, "findAddress").getReturnType()),
                                findFunction(otherTestService, "findAddressAsync").getReturnType());

        // TestObject and TestAddress are referenced by BOTH services but converted in one session,
        // so each appears exactly once in the namespace; the enum property converts inline so it
        // adds no complex type of its own
        Assertions.assertEquals(2, namespaceDefinition.getComplexC3Types().size());

        // an enum property converts as an EnumC3Type with the constant names, not through the
        // PojoTypeConverter catch-all (which would introspect Enum internals and fail on Class<E>)
        ObjectC3Type testObject = (ObjectC3Type) namespaceDefinition.getComplexC3Types()
                                                                    .stream()
                                                                    .filter(type -> type.getName().equals("TestObject"))
                                                                    .findFirst()
                                                                    .orElseThrow();
        C3Type statusType = testObject.getProperties()
                                      .stream()
                                      .filter(property -> property.getName().equals("status"))
                                      .findFirst()
                                      .orElseThrow()
                                      .getType();
        Assertions.assertEquals(new EnumC3Type().setNamespace(TestStatus.class.getPackageName())
                                                .setName(TestStatus.class.getSimpleName())
                                                .setValues(List.of("ACTIVE", "RETIRED")),
                                statusType);

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(namespaceDefinition);
        log.info("Namespace Definition\n"+json);
    }

    @Test
    public void testInheritedGenericSignaturesResolve() {
        NamespaceDefinition namespaceDefinition = schemaFactory.createForServices(Map.of(TestObjectCrudService.class, TestObjectCrudService.class));

        ServiceDefinition crudService = findService(namespaceDefinition, TestObjectCrudService.class);
        Assertions.assertEquals(2, crudService.getFunctions().size());

        // T and ID bind against TestObjectCrudService, so the inherited signatures convert concretely
        // instead of failing as unresolved type variables
        C3Type testObjectReference = new ReferenceC3Type(TestObject.class.getName());
        FunctionDefinition save = findFunction(crudService, "save");
        Assertions.assertEquals(new AsyncC3Type(testObjectReference), save.getReturnType());
        Assertions.assertEquals(testObjectReference, save.getParameters().getFirst().getType());
        // -parameters retains the source names, so the contract carries "entity", not arg0
        Assertions.assertEquals("entity", save.getParameters().getFirst().getName());

        FunctionDefinition findById = findFunction(crudService, "findById");
        Assertions.assertEquals(new AsyncC3Type(testObjectReference), findById.getReturnType());
        Assertions.assertEquals(new StringC3Type(), findById.getParameters().getFirst().getType());
        Assertions.assertEquals("id", findById.getParameters().getFirst().getName());
    }

    @Test
    public void testImplementationDecidesNamesAndAnnotations() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory.createForServices(Map.of(TestRenamedService.class, DefaultTestRenamedService.class));

        ServiceDefinition service = findService(namespaceDefinition, TestRenamedService.class);
        FunctionDefinition greet = findFunction(service, "greet");
        // the implementation's parameter name is what the named-argument binding resolves at invocation,
        // so it is the name the schema publishes — not the interface's "recipientName"
        Assertions.assertEquals("name", greet.getParameters().getFirst().getName());
        // @McpTool declared only on the implementation's override still marks the function
        McpToolC3Decorator decorator = greet.findDecorator(McpToolC3Decorator.class);
        Assertions.assertNotNull(decorator);
        Assertions.assertEquals("Greets the recipient", decorator.getDescription());
    }

    @Test
    public void testTypeLevelMcpToolMarksEveryFunction() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory.createForServices(Map.of(TestSweptService.class, TestSweptService.class));

        ServiceDefinition service = findService(namespaceDefinition, TestSweptService.class);

        // an unannotated method inherits the type-level description and hints
        McpToolC3Decorator swept = findFunction(service, "findByName").findDecorator(McpToolC3Decorator.class);
        Assertions.assertNotNull(swept);
        Assertions.assertEquals("Looks up test objects", swept.getDescription());
        Assertions.assertTrue(swept.isReadOnlyHint());

        // a method-level @McpTool overrides the type-level defaults for that method
        McpToolC3Decorator specific = findFunction(service, "countAll").findDecorator(McpToolC3Decorator.class);
        Assertions.assertNotNull(specific);
        Assertions.assertEquals("Counts every test object", specific.getDescription());
        Assertions.assertEquals("Count Objects", specific.getTitle());
        Assertions.assertFalse(specific.isReadOnlyHint());
    }

    @Test
    public void testUnconvertibleServiceOmitted() {
        NamespaceDefinition namespaceDefinition = schemaFactory.createForServices(Map.of(TestService.class, TestService.class,
                                                                                         BrokenTestService.class, BrokenTestService.class,
                                                                                         OtherTestService.class, OtherTestService.class));

        // BrokenTestService fails to convert and is omitted; the rest of the batch is unaffected
        Assertions.assertEquals(2, namespaceDefinition.getServices().size());
        findService(namespaceDefinition, TestService.class);
        findService(namespaceDefinition, OtherTestService.class);
        Assertions.assertEquals(2, namespaceDefinition.getComplexC3Types().size());
    }

    private ServiceDefinition findService(NamespaceDefinition namespaceDefinition, Class<?> serviceInterface) {
        return namespaceDefinition.getServices()
                                  .stream()
                                  .filter(service -> service.getQualifiedName().equals(serviceInterface.getPackageName() + "." + serviceInterface.getSimpleName()))
                                  .findFirst()
                                  .orElseThrow();
    }

    private FunctionDefinition findFunction(ServiceDefinition serviceDefinition, String name) {
        return serviceDefinition.getFunctions()
                                .stream()
                                .filter(function -> function.getName().equals(name))
                                .findFirst()
                                .orElseThrow();
    }

}
