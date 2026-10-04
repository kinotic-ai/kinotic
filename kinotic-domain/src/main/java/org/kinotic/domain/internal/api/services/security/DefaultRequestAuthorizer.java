package org.kinotic.domain.internal.api.services.security;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.domain.api.services.security.RequestAuthorizer;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.FunctionSpec;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;

/**
 * The {@link RequestAuthorizer} over the platform store: a function's check is read from its contract in the
 * service directory and kept, the object it names is read from the request with a parse that stops at it,
 * and the engine answers for the caller, or for the owner a delegate acts for.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRequestAuthorizer implements RequestAuthorizer {

    // a service's contract is read from the directory once and kept; the version in a request's CRI makes a
    // redeployed service a new key, and the retention bounds how long a platform service's new check waits
    private static final Duration CONTRACT_RETENTION = Duration.ofMinutes(5);
    private static final int CONTRACT_CAPACITY = 10_000;

    // the scope levels a check can be made on, each named by the template of its id, innermost first: a caller
    // missing one is checked on the next
    private static final List<Resource> SCOPE_LEVELS = List.of(new Resource(AuthzUtil.TENANT_TYPE, "{@tenantId}"),
                                                               new Resource(AuthzUtil.APPLICATION_TYPE, "{@applicationId}"),
                                                               new Resource(AuthzUtil.ORGANIZATION_TYPE, "{@organizationId}"));

    private final ObjectProvider<ServiceDirectory> directoryProvider;
    private final AuthzStoreService stores;
    private final RelationshipService relationships;
    private final JsonMapper jsonMapper;
    private final AsyncCache<String, Map<String, FunctionSpec>> contracts = Caffeine.newBuilder()
                                                                                    .maximumSize(CONTRACT_CAPACITY)
                                                                                    .expireAfterWrite(CONTRACT_RETENTION)
                                                                                    .buildAsync();

    @Override
    public Future<Void> authorize(CRI cri, Participant participant, String contentType, byte[] body) {
        Validate.notNull(cri, "cri cannot be null");
        Validate.notNull(participant, "participant cannot be null");
        Future<Void> ret;
        if (participant instanceof ApplicationParticipant || participant instanceof SystemParticipant) {
            // an application's store arrives with its contracts, and a platform operator's authority over
            // organizations with the platform's own resources; until then their zones alone admit them
            ret = Future.succeededFuture();
        } else if (participant instanceof ScopedParticipant scoped) {
            ret = spec(cri).compose(spec -> spec.check() == null
                    ? Future.succeededFuture()
                    : check(spec, scoped, contentType, body));
        } else {
            ret = Future.failedFuture(new AuthorizationException("No store answers for participant " + participant.getId()));
        }
        return ret;
    }

    private Future<FunctionSpec> spec(CRI cri) {
        ServiceDirectory directory = directoryProvider.getIfAvailable();
        Future<FunctionSpec> ret;
        if (directory == null) {
            ret = Future.succeededFuture(FunctionSpec.ZONE_ONLY);
        } else {
            // an entry is keyed as a registration keys it: the zone and the qualified name, never the scope
            String entryId = cri.hasZone() ? cri.zone() + "~" + cri.resourceName() : cri.resourceName();
            String key = entryId + "|" + cri.version();
            ret = KinoticUtil.toFuture(contracts.get(key, (k, executor) -> load(directory, entryId).toCompletionStage().toCompletableFuture()))
                             .map(functions -> cri.hasPath() ? functions.getOrDefault(cri.path().substring(1), FunctionSpec.ZONE_ONLY)
                                                             : FunctionSpec.ZONE_ONLY);
        }
        return ret;
    }

    // The specs of a service's functions by name; empty for a service the directory has no contract for
    private Future<Map<String, FunctionSpec>> load(ServiceDirectory directory, String entryId) {
        return directory.findEntry(entryId).map(entry -> {
            Map<String, FunctionSpec> ret = new HashMap<>();
            if (entry == null || entry.getServiceDefinition() == null) {
                log.debug("No contract covers {}; its zone alone admits requests to it", entryId);
            } else {
                for (FunctionDefinition function : entry.getServiceDefinition().getFunctions()) {
                    ret.put(function.getName(),
                            new FunctionSpec(function.findDecorator(AuthzCheckC3Decorator.class),
                                             function.getParameters().stream().map(ParameterDefinition::getName).toList()));
                }
            }
            return Map.copyOf(ret);
        });
    }

    private Future<Void> check(FunctionSpec spec, ScopedParticipant participant, String contentType, byte[] body) {
        AuthzCheckC3Decorator check = spec.check();
        Future<Void> ret;
        try {
            Resource checked = scopeLevelOf(check, participant.getScope());
            String resource = resolve(checked.type(), spec, participant, contentType, body);
            String objectId = resolve(checked.id(), spec, participant, contentType, body);
            String permissionResource = resolve(check.getPermissionResource(), spec, participant, contentType, body);
            RelationshipTuple relationship = new RelationshipTuple(subjectOf(participant),
                                                                   AuthzUtil.permissionName(permissionResource, check.getPermission()),
                                                                   AuthzUtil.object(resource, objectId));
            Consistency consistency = check.isConsistent() ? Consistency.HIGHER_CONSISTENCY : Consistency.MINIMIZE_LATENCY;
            ret = stores.platformModelId()
                        .compose(modelId -> relationships.check(PLATFORM, modelId, relationship, consistency))
                        .compose(allowed -> allowed
                                ? Future.succeededFuture()
                                : Future.failedFuture(new AuthorizationException("Not authorized: " + relationship.relation()
                                                                                         + " on " + relationship.object())));
        } catch (AuthorizationException e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

    /**
     * The object a check on the caller's scope is made on: the level the check names, or, for a caller whose
     * scope stops above it, the caller's own level, as an organization member asked for an application's
     * projects is checked on the organization. Every ancestor carries the permissions of the levels below it,
     * so the check keeps its meaning there. A check on anything but a scope level is kept as declared.
     */
    private static Resource scopeLevelOf(AuthzCheckC3Decorator check, ParticipantScope scope) {
        Resource ret = new Resource(check.getResource(), check.getObjectId());
        int level = SCOPE_LEVELS.indexOf(ret);
        if (level >= 0) {
            while (level < SCOPE_LEVELS.size() - 1
                    && scopeValue(AuthzUtil.templateReferences(SCOPE_LEVELS.get(level).id()).getFirst(), scope) == null) {
                level++;
            }
            ret = SCOPE_LEVELS.get(level);
        }
        return ret;
    }

    // A delegate acts with its owner's authority; everyone else with its own
    private static String subjectOf(Participant participant) {
        Map<String, String> metadata = participant.getMetadata();
        String owner = metadata != null ? metadata.get(DomainUtil.ON_BEHALF_OF_METADATA_KEY) : null;
        return AuthzUtil.object(AuthzUtil.USER_TYPE, owner != null ? owner : participant.getId());
    }

    private String resolve(String template, FunctionSpec spec, ScopedParticipant participant, String contentType, byte[] body) {
        String ret = template;
        for (String reference : AuthzUtil.templateReferences(template)) {
            String value = valueOf(reference, spec, participant, contentType, body);
            if (value == null || value.isEmpty()) {
                throw new AuthorizationException("The request names no " + reference + ", which its check needs");
            }
            ret = ret.replace("{" + reference + "}", value);
        }
        return ret;
    }

    private String valueOf(String reference, FunctionSpec spec, ScopedParticipant participant, String contentType, byte[] body) {
        String parameter = AuthzUtil.referencedParameter(reference);
        String ret;
        if (parameter == null) {
            ret = scopeValue(reference, participant.getScope());
        } else {
            int position = spec.parameters().indexOf(parameter);
            if (position < 0) {
                throw new AuthorizationException("The check names the parameter " + parameter + ", which the function does not have");
            }
            List<String> path = reference.length() > parameter.length()
                    ? Arrays.asList(reference.substring(parameter.length() + 1).split("\\."))
                    : List.of();
            ret = locate(contentType, body, parameter, position, path);
        }
        return ret;
    }

    private static String scopeValue(String reference, ParticipantScope scope) {
        return switch (reference) {
            case "@organizationId" -> scope.organizationId();
            case "@applicationId" -> scope.applicationId();
            case "@tenantId" -> scope.tenantId();
            default -> throw new AuthorizationException("The check names the unknown scope value " + reference);
        };
    }

    /**
     * The scalar at a parameter, or at a property path inside it, read from the body with a parse that stops
     * there: the element at the parameter's position of a positional body, the field of its name of a named one.
     */
    private String locate(String contentType, byte[] body, String parameter, int position, List<String> path) {
        if (body == null || body.length == 0) {
            throw new AuthorizationException("The request carries no body, in which its check names " + parameter);
        }
        boolean named = EventConstants.CONTENT_TYPE_NAMED_JSON.equals(contentType);
        if (!named && !EventConstants.CONTENT_TYPE_JSON.equals(contentType)) {
            throw new AuthorizationException("The request's body is " + contentType + ", in which no id can be located");
        }
        try (JsonParser parser = jsonMapper.createParser(body)) {
            JsonToken start = parser.nextToken();
            boolean found = named
                    ? start == JsonToken.START_OBJECT && field(parser, parameter)
                    : start == JsonToken.START_ARRAY && element(parser, position);
            for (int i = 0; found && i < path.size(); i++) {
                found = parser.currentToken() == JsonToken.START_OBJECT && field(parser, path.get(i));
            }
            return found && parser.currentToken() != null && parser.currentToken().isScalarValue()
                    ? parser.getValueAsString()
                    : null;
        }
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
