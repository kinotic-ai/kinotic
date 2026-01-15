package org.mindignited.structures.api.services.security.graphos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.kinotic.continuum.api.exceptions.AuthorizationException;
import org.kinotic.continuum.idl.api.schema.ObjectC3Type;
import org.mindignited.structures.api.domain.EntityOperation;
import org.mindignited.structures.api.domain.SecurityContext;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.domain.idl.decorators.EntityServiceDecorator;
import org.mindignited.structures.api.domain.idl.decorators.EntityServiceDecoratorsConfig;
import org.mindignited.structures.api.domain.idl.decorators.EntityServiceDecoratorsDecorator;
import org.mindignited.structures.api.domain.idl.decorators.PolicyDecorator;
import org.mindignited.structures.api.services.security.AuthorizationService;
import org.mindignited.structures.internal.api.services.impl.security.graphos.PolicyEvaluator;
import org.mindignited.structures.internal.api.services.impl.security.graphos.PolicyEvaluatorWithOperation;
import org.mindignited.structures.internal.api.services.impl.security.graphos.PolicyEvaluatorWithoutOperation;
import org.mindignited.structures.internal.api.services.impl.security.graphos.SharedPolicyManager;
import org.mindignited.structures.api.domain.DecoratedProperty;
import org.mindignited.structures.internal.utils.StructuresUtil;

public class StructurePolicyAuthorizationService implements AuthorizationService<EntityOperation> {

    private final Map<EntityOperation, PolicyEvaluator> operationEvaluators = new HashMap<>();
    private final PolicyEvaluatorWithoutOperation sharedEvaluator;
    private final String structureId;

    public StructurePolicyAuthorizationService(Structure structure,
                                               PolicyAuthorizer policyAuthorizer) {

        this.structureId = StructuresUtil.structureNameToId(structure.getApplicationId(), structure.getName());
        ObjectC3Type entityDefinition = structure.getEntityDefinition();

        // Get any Policies to apply to the Entity and its fields
        PolicyDecorator entityPolicies = entityDefinition.findDecorator(PolicyDecorator.class);

        Map<String, List<List<String>>> fieldPolicies = new HashMap<>();
        for(DecoratedProperty property : structure.getDecoratedProperties()){
            PolicyDecorator propertyPolicies = property.findDecorator(PolicyDecorator.class);
            if(propertyPolicies != null){
                fieldPolicies.put(property.getJsonPath(), propertyPolicies.getPolicies());
            }
        }

        SharedPolicyManager sharedPolicyManager = new SharedPolicyManager(entityPolicies != null ? entityPolicies.getPolicies() : null,
                                                                          fieldPolicies);
        sharedEvaluator = new PolicyEvaluatorWithoutOperation(policyAuthorizer, sharedPolicyManager);

        // Check if we have any policy decorators to apply to operations
        EntityServiceDecoratorsDecorator decorators = entityDefinition.findDecorator(EntityServiceDecoratorsDecorator.class);

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
    public CompletableFuture<Void> authorize(EntityOperation operation, SecurityContext securityContext) {
        try {
            PolicyEvaluator evaluator = operationEvaluators.get(operation);
            // if no operation policy use the
            if(evaluator == null) {
                evaluator = sharedEvaluator;
            }

            return evaluator.evaluatePolicies(securityContext).thenCompose(result -> {

                // Check if the operation is allowed i.e. findAll, save
                if(!result.operationAllowed()){

                    return CompletableFuture.failedFuture(new AuthorizationException("Operation %s not allowed.".formatted(operation)));

                } else if (!result.entityAllowed()) { // Check if access to the entity is allowed

                    return CompletableFuture.failedFuture(new AuthorizationException("Structure %s Entity access not allowed.".formatted(structureId)));

                } else { // Check if access to the individual fields are allowed

                    List<String> deniedFields = new ArrayList<>();
                    for(Map.Entry<String, Boolean> fieldResult : result.fieldResults().entrySet()){
                        if(!fieldResult.getValue()){
                            deniedFields.add(fieldResult.getKey());
                        }
                    }
                    if(!deniedFields.isEmpty()){
                        return CompletableFuture.failedFuture(new AuthorizationException("Structure %s Fields %s access not allowed.".formatted(structureId, deniedFields)));
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
