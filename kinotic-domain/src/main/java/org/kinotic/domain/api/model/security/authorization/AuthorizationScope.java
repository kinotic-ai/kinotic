package org.kinotic.domain.api.model.security.authorization;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class AuthorizationScope {
    private AuthorizationScopeKind kind;
    private String organizationId;
    private String applicationId;
    private String tenantId;

    public static AuthorizationScope from(org.kinotic.domain.api.model.security.participant.ScopedParticipant participant) {
        var ids = participant.getScope();
        var kind = ids.applicationId() == null ? AuthorizationScopeKind.ORGANIZATION
                : ids.tenantId() == null ? AuthorizationScopeKind.APPLICATION : AuthorizationScopeKind.APPLICATION_TENANT;
        return new AuthorizationScope().setKind(kind).setOrganizationId(ids.organizationId()).setApplicationId(ids.applicationId()).setTenantId(ids.tenantId());
    }

    public String key() {
        if (kind == null || organizationId == null || organizationId.isBlank()) throw new IllegalArgumentException("Organization scope is required");
        if (kind == AuthorizationScopeKind.ORGANIZATION && (applicationId != null || tenantId != null)) throw new IllegalArgumentException("Organization scope has no application or tenant");
        if (kind != AuthorizationScopeKind.ORGANIZATION && (applicationId == null || applicationId.isBlank())) throw new IllegalArgumentException("Application scope is required");
        if (kind == AuthorizationScopeKind.APPLICATION && tenantId != null) throw new IllegalArgumentException("Application scope has no tenant");
        if (kind == AuthorizationScopeKind.APPLICATION_TENANT && (tenantId == null || tenantId.isBlank())) throw new IllegalArgumentException("Tenant scope is required");
        return encode(kind.name(), organizationId, applicationId, tenantId);
    }

    public AuthorizationScope applicationScope() {
        return new AuthorizationScope().setKind(kind == AuthorizationScopeKind.ORGANIZATION ? kind : AuthorizationScopeKind.APPLICATION)
                .setOrganizationId(organizationId).setApplicationId(applicationId);
    }

    public static String encode(String... parts) {
        return java.util.Arrays.stream(parts).map(p -> p == null ? "-" : java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(p.getBytes(java.nio.charset.StandardCharsets.UTF_8))).collect(java.util.stream.Collectors.joining("."));
    }
}
