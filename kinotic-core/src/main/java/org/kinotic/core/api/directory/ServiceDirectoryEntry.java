package org.kinotic.core.api.directory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.idl.api.schema.ServiceDefinition;

import java.time.Instant;
import java.util.List;

/**
 * A directory record for one registered service contract. Scope is structural: {@link #organizationId} and
 * {@link #applicationId} both null means a system (OS) service, mirroring the participant model. An
 * {@link #applicationId} is never set without an {@link #organizationId}.
 * <p>
 * Contract fields are written when the service is published to the directory; {@link #online} and
 * {@link #lastStatusChange} are written only by the liveness owner and must be left untouched by entry upserts.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ServiceDirectoryEntry implements Identifiable<String> {

    /**
     * The service's qualified name ({@code zone.namespace.name}, omitting unset parts).
     */
    private String id;

    /**
     * The service's base resource address ({@code srv://...} without version), used to correlate this entry with
     * listener registrations for liveness.
     */
    private String serviceAddress;

    /**
     * The owning organization, or null for a system service.
     */
    private String organizationId;

    /**
     * The owning application, or null for a system or org-level service. Never set without an organization.
     */
    private String applicationId;

    /**
     * Customer provenance for the service, or null.
     */
    private String projectId;

    private String namespace;

    private String name;

    private String version;

    /**
     * The zone the service is addressable in, which drives tools-query visibility.
     */
    private String zone;

    private String description;

    /**
     * The C3 contract for the service, decorators included.
     */
    private ServiceDefinition serviceDefinition;

    /**
     * True when the service advertised itself with {@code @Publish(advertise = true)} and appears in directory
     * listings; false when the entry exists only to carry the service's MCP tools.
     */
    private boolean advertised;

    /**
     * True when any function carries the MCP tool decorator.
     */
    private boolean mcpExposed;

    /**
     * The ready-to-serve MCP tools for this service.
     */
    private List<McpToolDefinition> mcpTools;

    /**
     * True while the service currently has a registered handler.
     */
    private boolean online;

    private Instant lastStatusChange;

}
