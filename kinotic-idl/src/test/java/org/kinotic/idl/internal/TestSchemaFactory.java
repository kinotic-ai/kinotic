package org.kinotic.idl.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.ServiceDeclaration;
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
import org.kinotic.idl.internal.support.TestOverloadedService;
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
        NamespaceDefinition namespaceDefinition = schemaFactory.createForServices(List.of(new ServiceDeclaration(TestService.class, TestService.class),
                                                                                          new ServiceDeclaration(OtherTestService.class, OtherTestService.class)));

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
        NamespaceDefinition namespaceDefinition = schemaFactory.createForServices(List.of(new ServiceDeclaration(TestObjectCrudService.class, TestObjectCrudService.class)));

        ServiceDefinition crudService = findService(namespaceDefinition, TestObjectCrudService.class);
        Assertions.assertEquals(2, crudService.getFunctions().size());

        // T and ID bind against TestObjectCrudService, so the inherited signatures convert concretely
        // instead of failing as unresolved type variables
        C3Type testObjectReference = new ReferenceC3Type(TestObject.class.getName());
        FunctionDefinition save = findFunction(crudService, "save");
        Assertions.assertEquals(new AsyncC3Type(testObjectReference), save.getReturnType());
        Assertions.assertEquals(testObjectReference, save.getParameters().getFirst().getType());
        // -parameters retains the source names, so the interface carries "entity", not arg0
        Assertions.assertEquals("entity", save.getParameters().getFirst().getName());

        FunctionDefinition findById = findFunction(crudService, "findById");
        Assertions.assertEquals(new AsyncC3Type(testObjectReference), findById.getReturnType());
        Assertions.assertEquals(new StringC3Type(), findById.getParameters().getFirst().getType());
        Assertions.assertEquals("id", findById.getParameters().getFirst().getName());

        // an inherited function's description comes from the Javadoc on the generic base declaring it,
        // even though GenericCrudService itself carries no annotations
        McpToolC3Decorator inherited = findById.findDecorator(McpToolC3Decorator.class);
        Assertions.assertNotNull(inherited);
        Assertions.assertEquals("Finds the entity with the given id.", inherited.getDescription());
    }

    @Test
    public void testInterfaceDecidesNamesAndImplementationDecidesAnnotations() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory.createForServices(List.of(new ServiceDeclaration(TestRenamedService.class, DefaultTestRenamedService.class)));

        ServiceDefinition service = findService(namespaceDefinition, TestRenamedService.class);
        FunctionDefinition greet = findFunction(service, "greet");
        // AopUtils.selectInvocableMethod hands the invoker the interface's method, so the interface's
        // parameter name is what named-argument binding resolves — not the implementation's "name"
        Assertions.assertEquals("recipientName", greet.getParameters().getFirst().getName());
        // @McpTool declared only on the implementation's override still marks the function
        McpToolC3Decorator decorator = greet.findDecorator(McpToolC3Decorator.class);
        Assertions.assertNotNull(decorator);
        Assertions.assertEquals("Greets the recipient", decorator.getDescription());
        // an explicit description with no title still gets a derived title, qualified by the service name
        Assertions.assertEquals("Test Renamed Service Greet", decorator.getTitle());
    }

    @Test
    public void testTypeLevelMcpToolMarksEveryFunction() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory.createForServices(List.of(new ServiceDeclaration(TestSweptService.class, TestSweptService.class)));

        ServiceDefinition service = findService(namespaceDefinition, TestSweptService.class);

        // a documented method's description is the Javadoc main description extracted at compile time,
        // with inline tags resolved; the title still derives from the service and function names
        McpToolC3Decorator documented = findFunction(service, "findByName").findDecorator(McpToolC3Decorator.class);
        Assertions.assertNotNull(documented);
        Assertions.assertEquals("Finds the test object with the given name.", documented.getDescription());
        Assertions.assertEquals("Test Swept Service Find By Name", documented.getTitle());
        Assertions.assertTrue(documented.isReadOnlyHint());

        // no annotation description and no Javadoc: the description derives from the function name, the
        // title from the service and function names
        McpToolC3Decorator derived = findFunction(service, "countByName").findDecorator(McpToolC3Decorator.class);
        Assertions.assertNotNull(derived);
        Assertions.assertEquals("Count by name", derived.getDescription());
        Assertions.assertEquals("Test Swept Service Count By Name", derived.getTitle());

        // a method-level @McpTool overrides the type-level defaults for that method
        McpToolC3Decorator specific = findFunction(service, "countAll").findDecorator(McpToolC3Decorator.class);
        Assertions.assertNotNull(specific);
        Assertions.assertEquals("Counts every test object", specific.getDescription());
        Assertions.assertEquals("Count Objects", specific.getTitle());
        Assertions.assertFalse(specific.isReadOnlyHint());
    }

    @Test
    public void testOverloadedFunctionPublishesOnce() {
        NamespaceDefinition namespaceDefinition =
                schemaFactory.createForServices(List.of(new ServiceDeclaration(TestOverloadedService.class, TestOverloadedService.class)));

        ServiceDefinition service = findService(namespaceDefinition, TestOverloadedService.class);
        // IdlUtil.serviceFunctions keeps one method per name, the same rule ReflectiveServiceDescriptor
        // registers with, so the schema never advertises an overload the registry does not serve
        Assertions.assertEquals(1, service.getFunctions().size());
        findFunction(service, "find");
    }

    @Test
    public void testUnconvertibleServiceOmitted() {
        NamespaceDefinition namespaceDefinition = schemaFactory.createForServices(List.of(new ServiceDeclaration(TestService.class, TestService.class),
                                                                                          new ServiceDeclaration(BrokenTestService.class, BrokenTestService.class),
                                                                                          new ServiceDeclaration(OtherTestService.class, OtherTestService.class)));

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
