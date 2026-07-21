

package org.kinotic.core.api.event;

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
        // The scheme and authority (scope@resourceName) are case-insensitive URI parts, so they are
        // folded to lower case here — construction is the one choke point every address passes
        // through, which keeps both sides of a rendezvous in agreement regardless of the case a
        // caller supplied. The path is case-significant (it carries the function name) and the
        // version is stored verbatim.
        String authority = resourceName != null
                ? (scope != null ? scope.toLowerCase() + "@" : "") + resourceName.toLowerCase()
                : null;
        try {
            uri = new URI(scheme.toLowerCase(), authority, path, null, version);
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
        // Rebuilt rather than stored verbatim so a raw string folds identically to the
        // component constructor
        URI parsed = URI.create(rawCRI);
        String authority = parsed.getRawAuthority();
        try {
            uri = new URI(parsed.getScheme() != null ? parsed.getScheme().toLowerCase() : null,
                          authority != null ? authority.toLowerCase() : null,
                          parsed.getPath(),
                          parsed.getQuery(),
                          parsed.getFragment());
        } catch (URISyntaxException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }

    @Override
    public String scheme() {
        return uri.getScheme();
    }

    @Override
    public String scope() {
        // The raw authority is scope@resourceName; scope is the part before '@', null when absent.
        String authority = uri.getRawAuthority();
        if (authority == null) {
            return null;
        }
        int at = authority.indexOf('@');
        return at >= 0 ? authority.substring(0, at) : null;
    }

    @Override
    public boolean hasScope() {
        return scope() != null;
    }

    @Override
    public String resourceName() {
        String authority = uri.getRawAuthority();
        if (authority == null) {
            return null;
        }
        int at = authority.indexOf('@');
        return at >= 0 ? authority.substring(at + 1) : authority;
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
