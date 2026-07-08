

package org.kinotic.core.api.service;

import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.internal.utils.ZoneUtil;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The {@link ServiceIdentifier} identifies a {@link ServiceDescriptor}
 * Created by Navíd Mitchell 🤪 on 8/18/21.
 */
public class ServiceIdentifier {

    private final List<String> zones;

    private final String namespace;

    private final String name;

    // TODO: consider moving this somewhere else. It is not really appropriate for any {@link org.kinotic.rpc.api.annotations.Proxy} definitions
    private final String scope;

    private final String version;

    private final List<CRI> cris;

    public ServiceIdentifier(List<String> zones,
                             String namespace,
                             String name,
                             String scope,
                             String version) {
        Validate.notEmpty(name, "The name must not be empty");
        Validate.notEmpty(version, "The version must not be empty");
        // The name is the final dot separated label of the address, so a dot inside it would
        // change where the zone and namespace end when the address is parsed or pattern matched
        Validate.isTrue(!name.contains("."), "The name must not contain '.' but was '%s'", name);
        this.zones = zones != null ? List.copyOf(zones) : List.of();
        this.zones.forEach(ZoneUtil::validateZone);
        this.namespace = namespace;
        this.name = name;
        this.scope = scope;
        this.version = version;

        cris = this.zones.isEmpty()
                ? List.of(CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, scope, qualifiedName(null), null, version))
                : this.zones.stream()
                            .map(zone -> CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, scope, qualifiedName(zone), null, version))
                            .toList();
    }

    /**
     * The zones this {@link ServiceIdentifier} is addressable in
     * @return the zones, empty if un-zoned
     */
    public List<String> zones() {
        return zones;
    }

    /**
     * The namespace of this {@link ServiceIdentifier}
     * @return string containing the namespace or null if not provided
     */
    public String namespace() {
        return namespace;
    }

    /**
     * The name of this {@link ServiceIdentifier}
     * @return string containing the name
     */
    public String name() {
        return name;
    }


    /**
     * The scope of this {@link ServiceIdentifier}
     * The scope allows for multiple instances of the same service to be deployed to the cluster each having their own scope
     * A service can then be addressed by its scope
     * @return string containing the scope or null if not provided
     */
    public String scope() {
        return scope;
    }

    /**
     * The version for this service
     * @return string containing the version
     */
    public String version() {
        return version;
    }

    /**
     * Returns the fully qualified name this {@link ServiceIdentifier} is addressed by
     * This is the zone.namespace.name, omitting any part that is not set
     * @return string containing the qualified name
     * @throws IllegalStateException if this {@link ServiceIdentifier} has more than one zone and
     *         therefore more than one qualified name
     */
    public String qualifiedName(){
        Validate.validState(zones.size() <= 1,
                            "ServiceIdentifier has %s zones so there is no single qualifiedName", zones.size());
        return qualifiedName(zones.isEmpty() ? null : zones.getFirst());
    }

    private String qualifiedName(String zone) {
        String name = (namespace != null && !namespace.isEmpty() ? namespace + "." : "") + this.name;
        return zone != null ? zone + "." + name : name;
    }

    /**
     * The {@link CRI}s this {@link ServiceIdentifier} is addressable by, one per zone
     * @return the cris for this {@link ServiceIdentifier}, never empty
     */
    public List<CRI> cris(){
        return cris;
    }

    /**
     * The {@link CRI} that represents this {@link ServiceIdentifier}
     * @return the cri for this {@link ServiceIdentifier}
     * @throws IllegalStateException if this {@link ServiceIdentifier} has more than one zone and
     *         therefore more than one cri
     */
    public CRI cri(){
        Validate.validState(cris.size() == 1,
                            "ServiceIdentifier has %s cris so there is no single cri", cris.size());
        return cris.getFirst();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof ServiceIdentifier)) return false;

        ServiceIdentifier that = (ServiceIdentifier) o;

        return new EqualsBuilder().append(zones, that.zones())
                                  .append(namespace, that.namespace())
                                  .append(name, that.name())
                                  .append(scope, that.scope())
                                  .append(version, that.version())
                                  .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(zones).append(namespace).append(name).append(scope).append(version).toHashCode();
    }

    @Override
    public String toString() {
        return cris.stream().map(CRI::raw).collect(Collectors.joining(", "));
    }
}
