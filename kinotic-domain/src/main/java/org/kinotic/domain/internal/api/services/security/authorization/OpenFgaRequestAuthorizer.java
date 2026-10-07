package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.ServiceRequestAuthorizer;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.authorization.AuthorizationPermission;
import org.kinotic.domain.api.services.security.authorization.AuthorizationResourceResolver;
import org.kinotic.idl.api.schema.decorators.RequirePermissionC3Decorator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class OpenFgaRequestAuthorizer implements ServiceRequestAuthorizer {
    private final JsonMapper jsonMapper;
    private final ServiceDirectory directory;
    private final DefaultPermissionAuthorizationService authorization;
    private final List<AuthorizationResourceResolver> resourceResolvers;

    @Override
    public Future<Void> authorize(Event<byte[]> event) {
        return authorization.bounded(() -> {
            if (!(event.sender() instanceof ScopedParticipant participant)) return Future.failedFuture(new AuthorizationException("Authenticated scope is required"));
            if (authorization.bootstrap(participant)) return Future.succeededFuture();
            String qualified = event.cri().zone() == null ? event.cri().resourceName() : event.cri().zone() + "~" + event.cri().resourceName();
            return directory.findEntryById(qualified).compose(entry -> {
                if (entry == null || entry.getServiceDefinition() == null || event.cri().hasVersion() && !Objects.equals(entry.getVersion(), event.cri().version())) return Future.failedFuture(new AuthorizationException("No active permission contract"));
                if (entry.getOrganizationId() != null && !Objects.equals(entry.getOrganizationId(), participant.getScope().organizationId())
                        || entry.getApplicationId() != null && (!(participant instanceof ApplicationParticipant app) || !entry.getApplicationId().equals(app.getApplicationId()))) return Future.failedFuture(new AuthorizationException("Access denied"));
                String path = event.cri().path();
                String functionName = path != null && path.startsWith("/") ? path.substring(1) : path;
                var function = entry.getServiceDefinition().getFunctions().stream().filter(f -> f.getName().equals(functionName)).findFirst().orElse(null);
                if (function == null) return Future.failedFuture(new AuthorizationException("Unknown function"));
                var permission = function.findDecorator(RequirePermissionC3Decorator.class);
                if (permission == null) return Future.failedFuture(new AuthorizationException("Function has no permission contract"));
                AuthorizationCompiler.validatePermission(new AuthorizationPermission().setPermission(permission.getPermission()).setResourceType(permission.getResourceType()));
                String id = target(event, permission);
                Future<Void> owned = Future.succeededFuture();
                if (id != null && entry.getApplicationId() == null) {
                    var resolver = resourceResolvers.stream().filter(r -> r.supports(permission.getResourceType())).findFirst()
                            .orElseThrow(() -> new AuthorizationException("No ownership resolver for resource type"));
                    owned = resolver.requireOwned(permission.getResourceType(), id, DefaultPermissionAuthorizationService.scope(participant));
                }
                return owned.compose(ignored -> authorization.require(participant, DefaultPermissionAuthorizationService.scope(participant),
                        permission.getResourceType(), id, permission.getPermission()));
            });
        });
    }

    private String target(Event<byte[]> event, RequirePermissionC3Decorator permission) {
        String path = permission.getIdArgument();
        if (path == null || path.isEmpty()) return null;
        if (event.data() == null) throw new AuthorizationException("Missing resource arguments");
        String[] parts = path.split("\\.");
        String contentType = event.metadata().get(EventConstants.CONTENT_TYPE_HEADER);
        if (MimeTypeUtils.TEXT_PLAIN_VALUE.equals(contentType)) {
            if (parts.length != 1 || permission.getArgumentIndex() < 0) throw new AuthorizationException("Invalid text resource argument");
            String[] arguments = new String(event.data(), java.nio.charset.StandardCharsets.UTF_8).split("\\n", -1);
            if (permission.getArgumentIndex() >= arguments.length) throw new AuthorizationException("Missing resource argument");
            return resourceId(arguments[permission.getArgumentIndex()]);
        }
        try (var parser = jsonMapper.createParser(event.data())) {
            JsonNode value = null;
            var token = parser.nextToken();
            if (EventConstants.CONTENT_TYPE_NAMED_JSON.equals(contentType) && token == JsonToken.START_OBJECT) {
                while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    String name = parser.currentName(); parser.nextToken();
                    if (name.equals(parts[0])) { value = jsonMapper.readTree(parser); break; }
                    parser.skipChildren();
                }
            } else if (MimeTypeUtils.APPLICATION_JSON_VALUE.equals(contentType) && token == JsonToken.START_ARRAY && permission.getArgumentIndex() >= 0) {
                for (int index = 0; index <= permission.getArgumentIndex(); index++) {
                    var next = parser.nextToken();
                    if (next == null || next == JsonToken.END_ARRAY) throw new AuthorizationException("Missing resource argument");
                    if (index == permission.getArgumentIndex()) value = jsonMapper.readTree(parser);
                    else parser.skipChildren();
                }
            } else throw new AuthorizationException("Unsupported resource argument encoding");
            for (int index = 1; index < parts.length; index++) value = value == null ? null : value.get(parts[index]);
            if (value == null || !value.isString()) throw new AuthorizationException("A string resource id is required");
            return resourceId(value.stringValue());
        }
    }
    private String resourceId(String id) {
        if (id == null || id.isBlank() || id.equals("*") || id.length() > 512) throw new AuthorizationException("A resource id is required");
        return id;
    }
}
