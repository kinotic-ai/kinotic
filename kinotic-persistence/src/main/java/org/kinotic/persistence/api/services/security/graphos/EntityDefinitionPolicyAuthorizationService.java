package org.kinotic.persistence.api.services.security.graphos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.persistence.api.model.*;
import org.kinotic.persistence.api.model.idl.decorators.EntityServiceDecorator;
import org.kinotic.persistence.api.model.idl.decorators.EntityServiceDecoratorsConfig;
import org.kinotic.persistence.api.model.idl.decorators.EntityServiceDecoratorsDecorator;
import org.kinotic.persistence.api.model.idl.decorators.PolicyDecorator;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.internal.api.services.security.graphos.PolicyEvaluator;
import org.kinotic.persistence.internal.api.services.security.graphos.PolicyEvaluatorWithOperation;
import org.kinotic.persistence.internal.api.services.security.graphos.PolicyEvaluatorWithoutOperation;
import org.kinotic.persistence.internal.api.services.security.graphos.SharedPolicyManager;
import org.kinotic.persistence.internal.utils.PersistenceUtil;

public class EntityDefinitionPolicyAuthorizationService implements AuthorizationService<EntityOperation> {

    private final Map<EntityOperation, PolicyEvaluator> operationEvaluators = new HashMap<>();
    private final PolicyEvaluatorWithoutOperation sharedEvaluator;
    private final String entityDefinitionId;

    public EntityDefinitionPolicyAuthorizationService(EntityDefinition entityDefinition,
                                                      PolicyAuthorizer policyAuthorizer) {

        this.entityDefinitionId = PersistenceUtil.createEntityDefinitionId(entityDefinition.getOrganizationId(),
                                                                           entityDefinition.getApplicationId(),
                                                                           entityDefinition.getName());
        ObjectC3Type schema = entityDefinition.getSchema();

        // Get any Policies to apply to the Entity and its fields
        PolicyDecorator entityPolicies = schema.findDecorator(PolicyDecorator.class);

        Map<String, List<List<String>>> fieldPolicies = new HashMap<>();
        for(DecoratedProperty property : entityDefinition.getDecoratedProperties()){
            PolicyDecorator propertyPolicies = property.findDecorator(PolicyDecorator.class);
            if(propertyPolicies != null){
                fieldPolicies.put(property.getJsonPath(), propertyPolicies.getPolicies());
            }
        }

        SharedPolicyManager sharedPolicyManager = new SharedPolicyManager(entityPolicies != null ? entityPolicies.getPolicies() : null,
                                                                          fieldPolicies);
        sharedEvaluator = new PolicyEvaluatorWithoutOperation(policyAuthorizer, sharedPolicyManager);

        // Check if we have any policy decorators to apply to operations
        EntityServiceDecoratorsDecorator decorators = schema.findDecorator(EntityServiceDecoratorsDecorator.class);

        if(decorators != null){
            EntityServiceDecoratorsConfig config = decorators.getConfig();

            Map<EntityOperation, List<EntityServiceDecorator>> operationDecorators = config.getOperationDecoratorMap();
            for (Map.Entry<EntityOperation, List<EntityServiceDecorator>> entry : operationDecorators.entrySet()) {
                List<List<String>> operationPolicies = extractPolicies(entry.getValue());
                if(!operationPolicies.isEmpty()){
                    operationEvaluators.put(entry.getKey(), new PolicyEvaluatorWithOperation(policyAuthorizer, sharedPolicyManager, operationPolicies));
                }
            }
        }
    }

    @Override
    public CompletableFuture<Void> authorize(EntityOperation operation, EntityContext entityContext) {
        try {
            PolicyEvaluator evaluator = operationEvaluators.get(operation);
            // if no operation policy use the
            if(evaluator == null) {
                evaluator = sharedEvaluator;
            }

            return evaluator.evaluatePolicies(entityContext).thenCompose(result -> {

                // Check if the operation is allowed i.e. findAll, save
                if(!result.operationAllowed()){

                    return CompletableFuture.failedFuture(new AuthorizationException("Operation %s not allowed.".formatted(operation)));

                } else if (!result.entityAllowed()) { // Check if access to the entity is allowed

                    return CompletableFuture.failedFuture(new AuthorizationException("%s Entity access not allowed.".formatted(
                            entityDefinitionId)));

                } else { // Check if access to the individual fields are allowed

                    List<String> deniedFields = new ArrayList<>();
                    for(Map.Entry<String, Boolean> fieldResult : result.fieldResults().entrySet()){
                        if(!fieldResult.getValue()){
                            deniedFields.add(fieldResult.getKey());
                        }
                    }
                    if(!deniedFields.isEmpty()){
                        return CompletableFuture.failedFuture(new AuthorizationException("%s Fields %s access not allowed.".formatted(
                                entityDefinitionId, deniedFields)));
                    }else{
                        return CompletableFuture.completedFuture(null);
                    }

                }
            });

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private List<List<String>> extractPolicies(List<EntityServiceDecorator> decorators){
        for (EntityServiceDecorator decorator : decorators) {
            if(decorator instanceof PolicyDecorator policyDecorator) {
                return policyDecorator.getPolicies();
            }
        }
        return List.of();
    }


}
