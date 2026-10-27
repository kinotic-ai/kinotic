package org.kinotic.domain.internal.api.services.security;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityExceptionFactory;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.core.api.security.RequestAuthorizer;
import org.kinotic.domain.api.services.security.RequestPermissionResolver;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.FunctionSpec;
import org.kinotic.domain.internal.api.model.ParameterReference;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.schema.decorators.AuthzPermissionChoice;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

/**
 * The {@link RequestAuthorizer} over the stores: a function's check is read from its contract in the service
 * directory and kept, the object it names is read from the request, from a client's positional body with a parse
 * that stops once it has read the object, or from a tool call's arguments by name, and the engine answers for the
 * caller. A function is served without the engine only when its contract marks it
 * unchecked; a function no contract covers, of a service the directory holds no definition for or one the
 * definition leaves out, is refused. A check naming a permission resolver is made for the permission the resolver
 * decides from the request, one of the permissions the contract records it may require; a request it decides
 * nothing for, or one reaching a node without the resolver, is refused. The engine answers for the caller, or for the owner a delegate acts for, from the system's store for an
 * organization's members and the system's own staff, and from the application's store for an application's
 * users. A system user or one of the system's machines, whose scope names no organization, is checked
 * on the system itself wherever a check names the caller's scope, and an application's user on its tenant,
 * or on the application when it has none. A definition's rows are checked on the definition a request names: a
 * tenant's user on the definition within its tenant, or on the definition itself for a function its contract
 * declares acting across tenants, an organization's member on the definition in the system's store. What is
 * kept of a contract is dropped when the directory announces the contract written again. A refusal is answered "Not authorized", followed by its reason when {@code kinotic.debug} is on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRequestAuthorizer implements RequestAuthorizer {

    // a service's contract is read from the directory once and kept; the version in a request's CRI makes a
    // redeployed service a new key, and the retention bounds how long a system service's new check waits
    private static final Duration DEFINITION_RETENTION = Duration.ofMinutes(5);
    private static final int DEFINITION_CAPACITY = 10_000;

    // the scope levels a check can be made on, each named by the template of its id, innermost first: a caller
    // missing one is checked on the next, and one missing them all on the system
    private static final List<Resource> SCOPE_LEVELS = List.of(new Resource(AuthzConstants.TENANT_TYPE, "{" + AuthzConstants.TENANT_ID_REFERENCE + "}"),
                                                               new Resource(AuthzConstants.APPLICATION_TYPE, "{" + AuthzConstants.APPLICATION_ID_REFERENCE + "}"),
                                                               new Resource(AuthzConstants.ORGANIZATION_TYPE, "{" + AuthzConstants.ORGANIZATION_ID_REFERENCE + "}"));
    private static final Resource SYSTEM_LEVEL = new Resource(AuthzConstants.SYSTEM_TYPE, AuthzConstants.SYSTEM_OBJECT_ID);

    private final ObjectProvider<ServiceDirectory> directoryProvider;
    private final ObjectProvider<RequestPermissionResolver> permissionResolvers;
    private final RelationshipService relationships;
    private final JsonMapper jsonMapper;
    private final Vertx vertx;
    private final SecurityExceptionFactory securityExceptions;
    private final AsyncCache<String, Map<String, FunctionSpec>> definitions = Caffeine.newBuilder()
                                                                                    .maximumSize(DEFINITION_CAPACITY)
                                                                                    .expireAfterWrite(DEFINITION_RETENTION)
                                                                                    .buildAsync();

    /**
     * Listens for the directory announcing a contract written, and drops what is kept of it, so a function's
     * check changes for this node as soon as the directory holds the change.
     */
    @PostConstruct
    void listenForContractChanges() {
        // TODO: remove this and similar cache eviction logic infavor of more advanced cache eviction done in the kinotic-persistence module
        vertx.eventBus().<String>consumer(ServiceDirectory.CONTRACT_CHANGED_ADDRESS, message -> forget(message.body()));
    }

    private void forget(String entryId) {
        definitions.synchronous().invalidate(entryId);
    }

    @Override
    public Future<Void> authorize(CRI cri, Participant participant, String contentType, byte[] body) {
        return authorize(cri, participant, reference -> located(contentType, body, reference));
    }

    @Override
    public Future<Void> authorize(CRI cri, Participant participant, ObjectNode arguments) {
        Validate.notNull(arguments, "arguments cannot be null");
        return authorize(cri, participant, reference -> named(arguments, reference));
    }

    // Authorizes the request, reading a parameter the check names with the given function
    private Future<Void> authorize(CRI cri, Participant participant, Function<ParameterReference, String> arguments) {
        Validate.notNull(cri, "cri cannot be null");
        return withStore(participant, (scoped, store) -> spec(cri).compose(spec -> spec.check() == null
                ? Future.succeededFuture()
                : permissionOf(spec, scoped, arguments).compose(permission -> check(spec, permission, scoped, store, arguments))));
    }

    // Runs the authorization with the store answering for the caller: an application's own user's is the
    // application's store, every other caller's the system's
    private Future<Void> withStore(Participant participant, BiFunction<ScopedParticipant, String, Future<Void>> authorization) {
        Validate.notNull(participant, "participant cannot be null");
        Future<Void> ret;
        if (participant instanceof ScopedParticipant scoped) {
            String store = participant instanceof ApplicationParticipant application
                    ? AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getApplicationId())
                    : SYSTEM;
            ret = authorization.apply(scoped, store);
        } else {
            ret = Future.failedFuture(securityExceptions.notAuthorized("No store answers for participant of type {} with id {}",
                                                                       participant.getClass().getSimpleName(), participant.getId()));
        }
        return ret;
    }

    private Future<FunctionSpec> spec(CRI cri) {
        ServiceDirectory directory = directoryProvider.getIfAvailable();
        // an entry is keyed as a registration keys it: the zone and the qualified name, never the scope
        String entryId = cri.hasZone() ? cri.zone() + "~" + cri.resourceName() : cri.resourceName();
        Future<FunctionSpec> ret;
        if (directory == null) {
            ret = Future.failedFuture(securityExceptions.notAuthorized("No service directory holds the contract of {}, so its functions are refused",
                                                                       entryId));
        } else if (!cri.hasPath()) {
            ret = Future.failedFuture(securityExceptions.notAuthorized("The request names no function of {}", entryId));
        } else {
            // the directory holds one entry per id, so the requested version does not pick the contract
            String function = cri.path().substring(1);
            ret = KinoticUtil.toFuture(definitions.get(entryId, (k, executor) -> load(directory, entryId).toCompletionStage().toCompletableFuture()))
                             .map(functions -> {
                                 FunctionSpec spec = functions.get(function);
                                 if (spec == null) {
                                     throw securityExceptions.notAuthorized("No contract covers {} of {}; a function is served with a check,"
                                                                                    + " or marked unchecked", function, entryId);
                                 }
                                 return spec;
                             });
        }
        return ret;
    }

    // The specs of a service's functions by name, the checked and the marked unchecked: a function the contract
    // leaves out or marks neither way is absent, as every function of a service the directory has no definition for
    private Future<Map<String, FunctionSpec>> load(ServiceDirectory directory, String entryId) {
        return directory.findEntry(entryId).map(entry -> {
            Map<String, FunctionSpec> ret = new HashMap<>();
            if (entry == null || entry.getServiceDefinition() == null) {
                log.warn("No definition covers {}; its functions are refused until one is registered", entryId);
            } else {
                for (FunctionDefinition function : entry.getServiceDefinition().getFunctions()) {
                    AuthzCheckC3Decorator check = function.findDecorator(AuthzCheckC3Decorator.class);
                    List<String> parameters = function.getParameters().stream().map(ParameterDefinition::getName).toList();
                    if (check == null) {
                        log.warn("The contract of {} neither checks {} nor marks it unchecked; it is refused", entryId, function.getName());
                    } else {
                        ret.put(function.getName(), check.isUnchecked()
                                ? FunctionSpec.UNCHECKED
                                : specOf(check, parameters, entryId + "/" + function.getName()));
                    }
                }
            }
            return Map.copyOf(ret);
        });
    }

    private FunctionSpec specOf(AuthzCheckC3Decorator check, List<String> parameters, String function) {
        RequestPermissionResolver resolver = null;
        List<ParameterReference> resolverArguments = List.of();
        if (check.getPermissionResolver() != null) {
            resolver = permissionResolvers.orderedStream()
                                          .filter(candidate -> candidate.name().equals(check.getPermissionResolver()))
                                          .findFirst()
                                          .orElse(null);
            if (resolver == null) {
                log.warn("The contract of {} names the permission resolver {}, which this node does not have; it is refused",
                         function, check.getPermissionResolver());
            } else {
                resolverArguments = argumentsOf(resolver, parameters, function);
            }
        }
        return new FunctionSpec(check, referencesOf(check, parameters, function), resolver, resolverArguments);
    }

    // Where each parameter the resolver reads is read from, resolved once per contract rather than per request
    private static List<ParameterReference> argumentsOf(RequestPermissionResolver resolver, List<String> parameters, String function) {
        List<ParameterReference> ret = new ArrayList<>();
        for (String parameter : resolver.parameters()) {
            int position = parameters.indexOf(parameter);
            // the derivation refuses a contract whose resolver reads a parameter the function lacks, so a stored
            // one that does is corrupt
            if (position < 0) {
                throw new IllegalStateException("The permission resolver " + resolver.name() + " of " + function + " reads the parameter "
                                                        + parameter + ", which the function does not carry");
            }
            ret.add(new ParameterReference(parameter, parameter, position, List.of()));
        }
        return ret;
    }

    // Where each parameter reference of the check's id is read from, resolved once per contract rather than per
    // request; a reference to the caller's scope names no parameter
    private static List<ParameterReference> referencesOf(AuthzCheckC3Decorator check, List<String> parameters, String function) {
        List<ParameterReference> ret = new ArrayList<>();
        for (String reference : AuthzUtil.templateReferences(check.getResourceId())) {
            String parameter = AuthzUtil.referencedParameter(reference);
            if (parameter != null) {
                int position = parameters.indexOf(parameter);
                // the derivation refuses a contract referencing a parameter the function lacks, so a stored one
                // that does is corrupt, and its service is refused rather than served with a check that cannot be made
                if (position < 0) {
                    throw new IllegalStateException("The contract of " + function + " references the parameter " + parameter
                                                            + ", which the function does not carry");
                }
                List<String> path = reference.length() > parameter.length()
                        ? Arrays.asList(reference.substring(parameter.length() + 1).split("\\."))
                        : List.of();
                ret.add(new ParameterReference(reference, parameter, position, path));
            }
        }
        return ret;
    }

    // The permission the request needs: the one its check names, else the one its resolver decides from the
    // request, which must be one the contract records the resolver may require
    private Future<AuthzPermissionChoice> permissionOf(FunctionSpec spec,
                                                       ScopedParticipant participant,
                                                       Function<ParameterReference, String> arguments) {
        AuthzCheckC3Decorator check = spec.check();
        String name = check.getPermissionResolver();
        Future<AuthzPermissionChoice> ret;
        if (name == null) {
            ret = Future.succeededFuture(new AuthzPermissionChoice(check.getPermission(), null));
        } else if (spec.resolver() == null) {
            ret = Future.failedFuture(securityExceptions.notAuthorized("The check names the permission resolver {}, which this node does not have",
                                                                       name));
        } else {
            try {
                List<String> values = new ArrayList<>();
                for (ParameterReference argument : spec.resolverArguments()) {
                    String value = arguments.apply(argument);
                    if (value == null || value.isEmpty()) {
                        throw securityExceptions.notAuthorized("The request names no {}, which its permission resolver needs",
                                                               argument.parameter());
                    }
                    values.add(value);
                }
                // a lookup that fails is refused as one the caller holds no permission for, so the refusal tells
                // nothing about what exists
                ret = spec.resolver()
                          .resolve(participant, values)
                          .recover(e -> Future.failedFuture(e instanceof AuthorizationException
                                  ? e
                                  : securityExceptions.notAuthorized("The permission resolver {} decided nothing: {}", name, e.getMessage())))
                          .compose(choice -> isPossible(check, choice)
                                  ? Future.succeededFuture(choice)
                                  : Future.failedFuture(securityExceptions.notAuthorized("The permission resolver {} decided {}, which is not one it may require",
                                                                                         name, choice == null ? null : choice.getPermission())));
            } catch (AuthorizationException e) {
                ret = Future.failedFuture(e);
            }
        }
        return ret;
    }

    private static boolean isPossible(AuthzCheckC3Decorator check, AuthzPermissionChoice choice) {
        return choice != null && check.getPossiblePermissions().stream().anyMatch(possible -> possible.getPermission().equals(choice.getPermission()));
    }

    private Future<Void> check(FunctionSpec spec,
                               AuthzPermissionChoice permission,
                               ScopedParticipant participant,
                               String store,
                               Function<ParameterReference, String> arguments) {
        AuthzCheckC3Decorator check = spec.check();
        ParticipantScope scope = participant.getScope();
        Future<Void> ret;
        try {
            requireWithinCeiling(participant, check.getPermissionResource(), permission.getPermission());
            Resource checked = scopeLevelOf(check, scope);
            String resourceId = authzIdOf(checked.type(), resolve(checked.id(), spec, scope, arguments), scope);
            if (!AuthzUtil.isObjectId(resourceId)) {
                throw securityExceptions.notAuthorized("The request names the {} '{}', which names no resource", checked.type(), resourceId);
            }
            // A tenant's user is checked on the definition within its tenant, an object no store holds tuples for:
            // its two edges come with the check, the tenant's from the caller's scope and the definition's from the
            // request, and the model reaches it through a grant on the tenant only for a definition placed in the
            // application. A function acting on every tenant's rows is checked on the definition itself, which a
            // grant within one tenant does not reach
            boolean withinTenant = AuthzConstants.ENTITY_DEFINITION_TYPE.equals(checked.type()) && scope.tenantId() != null
                    && !check.isAcrossTenants();
            String object = withinTenant
                    ? AuthzUtil.object(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(resourceId, scope.tenantId()))
                    : AuthzUtil.object(checked.type(), resourceId);
            List<RelationshipTuple> edges = withinTenant ? AuthzGraphUtil.tenantDefinitionEdges(resourceId, scope.tenantId()) : List.of();
            RelationshipTuple relationship = new RelationshipTuple(AuthzGraphUtil.authzUser(participant),
                                                                   AuthzUtil.permissionName(check.getPermissionResource(), permission.getPermission()),
                                                                   object);
            Consistency consistency = check.isConsistent() ? Consistency.HIGHER_CONSISTENCY : Consistency.MINIMIZE_LATENCY;
            ret = relationships.check(store, relationship, consistency, edges)
                               .compose(allowed -> allowed
                                       ? Future.succeededFuture()
                                       : Future.failedFuture(refusal(relationship, permission.getReason())));
        } catch (AuthorizationException e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

    // A delegate exercises its owner's permissions within its type's ceiling, so a client the owner authorized reaches
    // only what that type of client needs; a delegate naming no type of a known ceiling is refused everything
    private void requireWithinCeiling(Participant participant, String type, String permission) {
        if (!AuthzGraphUtil.withinDelegateCeiling(participant, type, permission)) {
            throw securityExceptions.notAuthorized("{} of {} is beyond what a {} delegate may exercise", permission, type,
                                                   participant.getMetadata().get(DomainUtil.DELEGATE_TYPE_METADATA_KEY));
        }
    }

    // A refusal names the permission, and why the request needs it when a resolver decided it
    private AuthorizationException refusal(RelationshipTuple relationship, String reason) {
        return reason == null
                ? securityExceptions.notAuthorized("{} on {} for {}", relationship.relation(), relationship.object(), relationship.user())
                : securityExceptions.notAuthorized("{} on {} for {}: {}", relationship.relation(), relationship.object(), relationship.user(), reason);
    }

    /**
     * The object a check on the caller's scope is made on: the level the check names, or, for a caller whose
     * scope stops above it, the caller's own level, as an organization member asked for an application's
     * projects is checked on the organization, and a system user asked for either on the system.
     * Every ancestor carries the permissions of the levels below it, so the check keeps its meaning there. A
     * check on anything but a scope level is kept as declared.
     */
    private Resource scopeLevelOf(AuthzCheckC3Decorator check, ParticipantScope scope) {
        Resource ret = new Resource(check.getResource(), check.getResourceId());
        int level = SCOPE_LEVELS.indexOf(ret);
        if (level >= 0) {
            while (level < SCOPE_LEVELS.size()
                    && scopeValue(AuthzUtil.templateReferences(SCOPE_LEVELS.get(level).id()).getFirst(), scope) == null) {
                level++;
            }
            ret = level < SCOPE_LEVELS.size() ? SCOPE_LEVELS.get(level) : SYSTEM_LEVEL;
        }
        return ret;
    }

    // An application or a project is named within the caller's organization, so a caller outside every
    // organization names none
    private String authzIdOf(String type, String id, ParticipantScope scope) {
        try {
            return AuthzGraphUtil.authzId(type, scope.organizationId(), id);
        } catch (IllegalArgumentException e) {
            throw securityExceptions.notAuthorized("The check names {} {}, which only a caller in an organization addresses",
                                                   type, id);
        }
    }

    private String resolve(String template, FunctionSpec spec, ParticipantScope scope, Function<ParameterReference, String> arguments) {
        String ret = template;
        for (String reference : AuthzUtil.templateReferences(template)) {
            ParameterReference parameter = spec.reference(reference);
            String value = parameter == null
                    ? scopeValue(reference, scope)
                    : arguments.apply(parameter);
            if (value == null || value.isEmpty()) {
                throw securityExceptions.notAuthorized("The request names no {}, which its check needs", reference);
            }
            ret = ret.replace("{" + reference + "}", value);
        }
        return ret;
    }

    private String scopeValue(String reference, ParticipantScope scope) {
        return switch (reference) {
            case AuthzConstants.ORGANIZATION_ID_REFERENCE -> scope.organizationId();
            case AuthzConstants.APPLICATION_ID_REFERENCE -> scope.applicationId();
            case AuthzConstants.TENANT_ID_REFERENCE -> scope.tenantId();
            default -> throw securityExceptions.notAuthorized("The check names the unknown scope value {}", reference);
        };
    }

    /**
     * The scalar at a parameter, or at a property path inside it, read from a positional body with a parse that
     * stops once it has read the parameter: the element at the parameter's position, and the objects the path
     * descends into.
     *
     * @throws AuthorizationException when the body is not a positional JSON body, or an object the path descends
     *                                into names a property twice
     */
    private String located(String contentType, byte[] body, ParameterReference reference) {
        if (body == null || body.length == 0) {
            throw securityExceptions.notAuthorized("The request carries no body, in which its check names {}", reference.parameter());
        }
        if (!EventConstants.CONTENT_TYPE_JSON.equals(contentType)) {
            throw securityExceptions.notAuthorized("The request's body is {}, in which no id can be located", contentType);
        }
        String ret;
        // a service binds the last of a property named twice, which need not be the one read here, so the parser
        // refuses a repeated name and each object the path descends into is read to its end
        try (JsonParser parser = jsonMapper.reader().with(StreamReadFeature.STRICT_DUPLICATE_DETECTION).createParser(body)) {
            boolean found = parser.nextToken() == JsonToken.START_ARRAY && element(parser, reference.position());
            List<String> path = reference.path();
            for (int i = 0; found && i < path.size(); i++) {
                found = parser.currentToken() == JsonToken.START_OBJECT && field(parser, path.get(i));
            }
            ret = found && parser.currentToken() != null && parser.currentToken().isScalarValue()
                    ? parser.getValueAsString()
                    : null;
            if (ret != null) {
                finish(parser, path.size());
            }
        } catch (JacksonException e) {
            // a malformed body and one past the parser's limits on length and nesting are refused alike
            throw securityExceptions.notAuthorized("The request's body cannot be read for its check: {}", e.getOriginalMessage());
        }
        return ret;
    }

    // Reads past the end of the given number of objects the parser is inside, the innermost first
    private static void finish(JsonParser parser, int objects) {
        int open = objects;
        JsonToken token = parser.currentToken();
        while (open > 0 && token != null) {
            token = parser.nextToken();
            if (token == JsonToken.END_OBJECT) {
                open--;
            } else if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                parser.skipChildren();
            }
        }
    }

    // The scalar at a parameter, or at a property path inside it, of a tool call's arguments
    private static String named(ObjectNode arguments, ParameterReference reference) {
        JsonNode node = arguments.get(reference.parameter());
        for (int i = 0; node != null && i < reference.path().size(); i++) {
            node = node.isObject() ? node.get(reference.path().get(i)) : null;
        }
        return node != null && node.isValueNode() && !node.isNull() ? node.asString() : null;
    }

    // Leaves the parser on the value of the named field of the object it has just entered, false when there is none
    private static boolean field(JsonParser parser, String name) {
        boolean ret = false;
        while (!ret && parser.nextToken() == JsonToken.PROPERTY_NAME) {
            if (name.equals(parser.currentName())) {
                parser.nextToken();
                ret = true;
            } else {
                parser.nextToken();
                parser.skipChildren();
            }
        }
        return ret;
    }

    // Leaves the parser on the element at the position of the array it has just entered, false when it is shorter
    private static boolean element(JsonParser parser, int position) {
        boolean ret = true;
        for (int i = 0; ret && i <= position; i++) {
            JsonToken token = parser.nextToken();
            if (token == null || token == JsonToken.END_ARRAY) {
                ret = false;
            } else if (i < position) {
                parser.skipChildren();
            }
        }
        return ret;
    }
}
