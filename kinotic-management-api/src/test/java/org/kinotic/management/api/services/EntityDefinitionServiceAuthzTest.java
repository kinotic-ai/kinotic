package org.kinotic.management.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.directory.ServiceDeclaration;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.management.internal.api.services.DefaultEntityDefinitionService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the checks {@link EntityDefinitionService} publishes for the functions that change what a definition
 * stores: un-publishing drops the definition's rows, so it is checked as deleting the definition is, on the
 * definition its argument names, while publishing stays an edit.
 */
public class EntityDefinitionServiceAuthzTest {

    @Test
    public void unPublishIsCheckedAsDeletingTheDefinition() {
        ServiceDefinition service = service();

        AuthzCheckC3Decorator unPublish = check(service, "unPublish");
        AuthzCheckC3Decorator deleteById = check(service, "deleteById");
        assertEquals(AuthzConstants.CAN_DELETE, deleteById.getPermission());
        assertEquals(deleteById.getPermission(), unPublish.getPermission());
        assertEquals(deleteById.getResource(), unPublish.getResource());
        assertEquals(deleteById.getPermissionResource(), unPublish.getPermissionResource());
        assertEquals(EntityDefinitionService.RESOURCE_TYPE, unPublish.getResource());
        assertEquals("{entityDefinitionId}", unPublish.getResourceId());
    }

    @Test
    public void publishIsCheckedAsEditingTheDefinition() {
        AuthzCheckC3Decorator publish = check(service(), "publish");

        assertEquals(AuthzConstants.CAN_EDIT, publish.getPermission());
        assertEquals(EntityDefinitionService.RESOURCE_TYPE, publish.getResource());
        assertEquals("{entityDefinitionId}", publish.getResourceId());
    }

    private static ServiceDefinition service() {
        return McpServiceSchemaTest.schemaFactory()
                                   .createForServices(List.of(new ServiceDeclaration(EntityDefinitionService.class,
                                                                                     DefaultEntityDefinitionService.class)))
                                   .getServices()
                                   .stream()
                                   .findFirst()
                                   .orElseThrow();
    }

    private static AuthzCheckC3Decorator check(ServiceDefinition service, String functionName) {
        AuthzCheckC3Decorator ret = service.getFunctions()
                                           .stream()
                                           .filter(function -> function.getName().equals(functionName))
                                           .findFirst()
                                           .orElseThrow()
                                           .findDecorator(AuthzCheckC3Decorator.class);
        assertNotNull(ret, functionName + " carries no check");
        return ret;
    }
}
