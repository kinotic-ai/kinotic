package org.kinotic.idl.internal.directory;

import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.SchemaService;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzRoleDeclaration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a definition a runtime declares derives as a {@code @Publish} interface does: each function of a
 * service declaring a resource gets the check derived from its name and parameters or taken from the check it
 * declares, with the same refusals, so a TypeScript service and a Java one are indistinguishable to the
 * directory, and a service declaring no resource is stored as declared.
 */
@SpringBootTest
public class CheckDerivationTest {

    @Autowired
    private SchemaService schemaService;

    @Test
    public void aDeclaredDefinitionDerivesItsChecks() {
        ServiceDefinition declared = reports(
                function("findReports", null),
                function("findById", null, "reportId"),
                function("generate", new AuthzCheckC3Decorator().setPermission("can_generate").setConsistent(true), "reportId", "options"),
                function("ping", new AuthzCheckC3Decorator().setUnchecked(true)),
                function("archive", new AuthzCheckC3Decorator().setPermission("can_archive").setResource("tenant").setImplies(List.of("can_view")), "arg0", "tags"));

        ServiceDefinition service = schemaService.deriveChecks(declared);

        assertEquals("com.acme.reports.ReportService", service.getQualifiedName());
        AuthzResourceC3Decorator resource = service.findDecorator(AuthzResourceC3Decorator.class);
        assertEquals("report", resource.getResourceType());
        assertEquals("tenant", resource.getParent());
        assertEquals(List.of("report.generator"), resource.getRoles().stream().map(AuthzRoleDeclaration::getId).toList());
        assertEquals(5, service.getFunctions().size());

        // a listing names no report, so it is checked on the caller's tenant
        assertEquals("tenant:{@tenantId}", at(check(service, "findReports")));
        assertEquals("report_can_view", permission(check(service, "findReports")));
        // the parameter named for the type is the report's id
        assertEquals("report:{reportId}", at(check(service, "findById")));
        assertEquals("can_view", check(service, "findById").getPermission());
        assertEquals("report:{reportId}", at(check(service, "generate")));
        assertEquals("report_can_generate", permission(check(service, "generate")));
        assertTrue(check(service, "generate").isConsistent());
        // a function declared unchecked carries the mark, and no check
        assertTrue(check(service, "ping").isUnchecked());
        assertNull(check(service, "ping").getPermission());
        // a check on the parent is the type's permission within it
        assertEquals("tenant:{@tenantId}", at(check(service, "archive")));
        assertEquals("report_can_archive", permission(check(service, "archive")));
        assertEquals(List.of("can_view"), check(service, "archive").getImplies());
        // the function's types are kept as declared
        FunctionDefinition generate = function(service, "generate");
        assertEquals(List.of("reportId", "options"), generate.getParameters().stream().map(p -> p.getName()).toList());
        assertInstanceOf(StringC3Type.class, generate.getParameters().getFirst().getType());
        assertInstanceOf(AnyC3Type.class, generate.getReturnType());
        // the declared definition is left as it is
        assertNull(function(declared, "findReports").getDecorators());
        assertTrue(function(declared, "ping").findDecorator(AuthzCheckC3Decorator.class).isUnchecked());
    }

    @Test
    public void aFunctionDerivingNoCheckAndDeclaringNoneIsRefused() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> schemaService.deriveChecks(reports(function("render", null, "reportId"))));

        assertTrue(e.getMessage().contains("render on com.acme.reports.ReportService"), e.getMessage());
    }

    @Test
    public void aDefinitionDeclaredWithoutTheFlagsDerives() throws Exception {
        // a runtime's definition names only what it declares
        String declared = """
                {"namespace":"com.acme.reports","name":"ReportService",
                 "decorators":[{"type":"AuthzResource","resourceType":"report","parent":"tenant",
                                "roles":[{"id":"report.generator","permissions":["can_generate"]}]}],
                 "functions":[{"name":"findReports","returnType":{"type":"any"}},
                              {"name":"generate","returnType":{"type":"any"},
                               "parameters":[{"name":"name","type":{"type":"string"}}],
                               "decorators":[{"type":"AuthzCheck","permission":"can_generate","resource":"tenant"}]}]}
                """;
        ServiceDefinition definition = JsonMapper.builder().build().readValue(declared, ServiceDefinition.class);

        ServiceDefinition service = schemaService.deriveChecks(definition);

        AuthzCheckC3Decorator generate = check(service, "generate");
        assertEquals("tenant:{@tenantId}", at(generate));
        assertEquals("report_can_generate", permission(generate));
        assertFalse(generate.isConsistent());
        assertEquals("report_can_view", permission(check(service, "findReports")));
    }

    @Test
    public void aTemplateNamingAnUnknownParameterIsRefused() {
        AuthzCheckC3Decorator check = new AuthzCheckC3Decorator().setPermission("can_generate").setResourceId("{missing}");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> schemaService.deriveChecks(reports(function("generate", check, "reportId"))));

        assertTrue(e.getMessage().contains("missing"), e.getMessage());
    }

    @Test
    public void aDefinitionWithoutAResourceIsStoredAsDeclared() {
        ServiceDefinition plain = new ServiceDefinition().setNamespace("com.acme.reports").setName("ReportService");
        plain.addFunction(function("render", null, "reportId"));

        ServiceDefinition service = schemaService.deriveChecks(plain);

        assertNull(service.getDecorators());
        assertNull(function(service, "render").getDecorators());

        ServiceDefinition checked = new ServiceDefinition().setNamespace("com.acme.reports").setName("ReportService");
        checked.addFunction(function("render", new AuthzCheckC3Decorator().setPermission("can_render"), "reportId"));
        assertTrue(assertThrows(IllegalStateException.class, () -> schemaService.deriveChecks(checked)).getMessage().contains("no resource"));

        ServiceDefinition nameless = reports();
        nameless.setNamespace(null);
        assertThrows(IllegalArgumentException.class, () -> schemaService.deriveChecks(nameless));
    }

    @Test
    public void aRoleNotNamedAfterTheTypeIsRefused() {
        ServiceDefinition service = reports();
        service.findDecorator(AuthzResourceC3Decorator.class)
               .setRoles(List.of(new AuthzRoleDeclaration().setId("invoice.reader").setPermissions(List.of("can_view"))));

        assertTrue(assertThrows(IllegalStateException.class, () -> schemaService.deriveChecks(service)).getMessage().contains("report."));
    }

    private static ServiceDefinition reports(FunctionDefinition... functions) {
        AuthzResourceC3Decorator resource = new AuthzResourceC3Decorator()
                .setResourceType("report")
                .setParent("tenant")
                .setRoles(List.of(new AuthzRoleDeclaration().setId("report.generator").setPermissions(List.of("can_generate"))));
        ServiceDefinition ret = new ServiceDefinition().setNamespace("com.acme.reports").setName("ReportService");
        ret.setDecorators(List.of(resource));
        for (FunctionDefinition function : functions) {
            ret.addFunction(function);
        }
        return ret;
    }

    private static FunctionDefinition function(String name, AuthzCheckC3Decorator check, String... parameters) {
        FunctionDefinition ret = new FunctionDefinition().setName(name).setReturnType(new AnyC3Type());
        for (String parameter : parameters) {
            ret.addParameter(parameter, new StringC3Type());
        }
        if (check != null) {
            ret.setDecorators(List.of(check));
        }
        return ret;
    }

    private static FunctionDefinition function(ServiceDefinition service, String name) {
        return service.getFunctions().stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow();
    }

    private static AuthzCheckC3Decorator check(ServiceDefinition service, String name) {
        return function(service, name).findDecorator(AuthzCheckC3Decorator.class);
    }

    private static String at(AuthzCheckC3Decorator check) {
        return check.getResource() + ":" + check.getResourceId();
    }

    private static String permission(AuthzCheckC3Decorator check) {
        return check.getPermissionResource() + "_" + check.getPermission();
    }
}
