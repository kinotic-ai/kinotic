

package org.kinotic.rpc.api.event;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.net.URI;
import java.net.URISyntaxException;

/**
 *
 * Created by Navid Mitchell on 5/1/20
 */
class DefaultCRI implements CRI {

    private final URI uri;

    public DefaultCRI(String scheme, String scope, String resourceName, String path, String version) {
        try {
            uri = new URI(scheme, scope,  resourceName, -1, path,null, version);
        } catch (URISyntaxException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }

    /**
     * Will create a {@link CRI} from a raw string
     *
     * @param rawCRI the raw string to create from an {@link CRI}
     */
    public DefaultCRI(String rawCRI) {
        uri = URI.create(rawCRI);
    }

    @Override
    public String scheme() {
        return uri.getScheme();
    }

    @Override
    public String scope() {
        return uri.getRawUserInfo();
    }

    @Override
    public boolean hasScope() {
        return uri.getRawUserInfo() != null;
    }

    @Override
    public String resourceName() {
        return uri.getHost();
    }

    @Override
    public String version() {
        return uri.getRawFragment();
    }

    @Override
    public boolean hasVersion() {
        return uri.getRawFragment() != null;
    }

    @Override
    public String path() {
        return uri.getRawPath();
    }

    @Override
    public boolean hasPath() {
        return uri.getRawPath() != null;
    }

    @Override
    public String baseResource() {
        StringBuilder sb = new StringBuilder(scheme());
        sb.append("://");
        if(hasScope()){
            sb.append(scope());
            sb.append("@");
        }
        sb.append(resourceName());

        return sb.toString();
    }

    @Override
    public String raw() {
        return uri.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DefaultCRI that = (DefaultCRI) o;

        return new EqualsBuilder()
                .append(uri.toString(), that.toString())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(uri.toString())
                .toHashCode();
    }

    @Override
    public String toString() {
        return uri.toString();
    }
}
