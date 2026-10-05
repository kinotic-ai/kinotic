package org.kinotic.idl.internal.directory;

import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.AuthzCheckDeclaration;
import org.kinotic.idl.api.directory.AuthzResourceDeclaration;
import org.kinotic.idl.api.directory.FunctionContract;
import org.kinotic.idl.api.directory.SchemaService;
import org.kinotic.idl.api.directory.ServiceContract;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
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
 * Verifies a contract a runtime declares converts as a {@code @Publish} interface does: its functions carry the
 * declared parameter names, typed as anything, and each function's check is derived from its name and
 * parameters or taken from what it declares, with the same refusals, so a TypeScript service and a Java one are
 * indistinguishable to the directory.
 */
@SpringBootTest
public class ServiceContractConversionTest {

    @Autowired
    private SchemaService schemaService;

    @Test
    public void aDeclaredContractConvertsWithItsChecksDerived() {
        ServiceDefinition service = schemaService.createForContract(reports(
                new FunctionContract("findReports", List.of(), null),
                new FunctionContract("findById", List.of("reportId"), null),
                new FunctionContract("generate", List.of("reportId", "options"), new AuthzCheckDeclaration("can_generate", null, null, null, false, true)),
                new FunctionContract("ping", List.of(), new AuthzCheckDeclaration(null, null, null, null, true, false)),
                new FunctionContract("archive", List.of("arg0", "tags"), new AuthzCheckDeclaration("can_archive", "tenant", null, List.of("can_view"), false, false))));

        assertEquals("com.acme.reports.ReportService", service.getQualifiedName());
        AuthzResourceC3Decorator resource = service.findDecorator(AuthzResourceC3Decorator.class);
        assertEquals("report", resource.getResourceType());
        assertEquals("tenant", resource.getParent());
        assertEquals(List.of("report.generator"), resource.getRoles().stream().map(AuthzRoleDeclaration::getId).toList());
        assertEquals(5, service.getFunctions().size());

        // a listing names no report, so it is checked on the caller's tenant
        assertEquals("tenant:{@tenantId}", at(check(service, "findReports")));
        assertEquals("report_can_view", permission(check(service, "findReports")));
        // the untyped parameter named for the type is the report's id
        assertEquals("report:{reportId}", at(check(service, "findById")));
        assertEquals("can_view", check(service, "findById").getPermission());
        assertEquals("report:{reportId}", at(check(service, "generate")));
        assertEquals("report_can_generate", permission(check(service, "generate")));
        assertTrue(check(service, "generate").isConsistent());
        assertNull(check(service, "ping"));
        // a check on the parent is the type's permission within it
        assertEquals("tenant:{@tenantId}", at(check(service, "archive")));
        assertEquals("report_can_archive", permission(check(service, "archive")));
        assertEquals(List.of("can_view"), check(service, "archive").getImplies());
        FunctionDefinition generate = function(service, "generate");
        assertEquals(List.of("reportId", "options"), generate.getParameters().stream().map(p -> p.getName()).toList());
        assertInstanceOf(AnyC3Type.class, generate.getParameters().getFirst().getType());
        assertInstanceOf(AnyC3Type.class, generate.getReturnType());
    }

    @Test
    public void aFunctionDerivingNoCheckAndDeclaringNoneRefusesTheContract() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> schemaService.createForContract(reports(new FunctionContract("render", List.of("reportId"), null))));

        assertTrue(e.getMessage().contains("render on com.acme.reports.ReportService"), e.getMessage());
    }

    @Test
    public void aContractPublishedWithoutTheFlagsConverts() throws Exception {
        // a client's contract leaves a flag out where it is not set
        String published = """
                {"namespace":"com.acme.reports","name":"ReportService","version":null,"zone":"app.acme.crm",
                 "resource":{"value":"report","parent":"tenant","roles":[{"id":"report.generator","permissions":["can_generate"]}]},
                 "functions":[{"name":"findReports","parameters":[],"check":null},
                              {"name":"generate","parameters":["name"],"check":{"permission":"can_generate","resource":"tenant"}}]}
                """;
        ServiceContract contract = JsonMapper.builder().build().readValue(published, ServiceContract.class);

        ServiceDefinition service = schemaService.createForContract(contract);

        AuthzCheckC3Decorator generate = check(service, "generate");
        assertEquals("tenant:{@tenantId}", at(generate));
        assertEquals("report_can_generate", permission(generate));
        assertFalse(generate.isConsistent());
    }

    @Test
    public void aTemplateNamingAnUnknownParameterRefusesTheContract() {
        AuthzCheckDeclaration check = new AuthzCheckDeclaration("can_generate", null, "{missing}", null, false, false);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> schemaService.createForContract(reports(new FunctionContract("generate", List.of("reportId"), check))));

        assertTrue(e.getMessage().contains("missing"), e.getMessage());
    }

    @Test
    public void aContractWithoutAResourceOrWithAFunctionTwiceIsRefused() {
        ServiceContract bare = new ServiceContract("com.acme.reports", "ReportService", "1.0.0", "app.acme.crm", null, List.of());
        assertTrue(assertThrows(IllegalStateException.class, () -> schemaService.createForContract(bare)).getMessage().contains("no resource"));

        ServiceContract twice = reports(new FunctionContract("findReports", List.of(), null), new FunctionContract("findReports", List.of("page"), null));
        assertTrue(assertThrows(IllegalStateException.class, () -> schemaService.createForContract(twice)).getMessage().contains("twice"));

        ServiceContract nameless = new ServiceContract(null, "ReportService", "1.0.0", "app.acme.crm", reports().resource(), List.of());
        assertThrows(IllegalArgumentException.class, () -> schemaService.createForContract(nameless));
    }

    @Test
    public void aRoleNotNamedAfterTheTypeRefusesTheContract() {
        AuthzResourceDeclaration resource = new AuthzResourceDeclaration("report", "tenant", null, null,
                                                                         List.of(new AuthzRoleDeclaration().setId("invoice.reader").setPermissions(List.of("can_view"))));
        ServiceContract contract = new ServiceContract("com.acme.reports", "ReportService", "1.0.0", "app.acme.crm", resource, List.of());

        assertTrue(assertThrows(IllegalStateException.class, () -> schemaService.createForContract(contract)).getMessage().contains("report."));
    }

    private static ServiceContract reports(FunctionContract... functions) {
        AuthzResourceDeclaration resource = new AuthzResourceDeclaration("report", "tenant", null, null,
                                                                         List.of(new AuthzRoleDeclaration().setId("report.generator").setPermissions(List.of("can_generate"))));
        return new ServiceContract("com.acme.reports", "ReportService", "1.0.0", "app.acme.crm", resource, List.of(functions));
    }

    private static FunctionDefinition function(ServiceDefinition service, String name) {
        return service.getFunctions().stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow();
    }

    private static AuthzCheckC3Decorator check(ServiceDefinition service, String name) {
        return function(service, name).findDecorator(AuthzCheckC3Decorator.class);
    }

    private static String at(AuthzCheckC3Decorator check) {
        return check.getResource() + ":" + check.getObjectId();
    }

    private static String permission(AuthzCheckC3Decorator check) {
        return check.getPermissionResource() + "_" + check.getPermission();
    }
}
