

package org.kinotic.core.api.event;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 *
 * Created by Navid Mitchell on 5/1/20
 */
class DefaultCRI implements CRI {

    private static final char ZONE_DELIMITER = '~';

    private final URI uri;

    public DefaultCRI(String scheme, String scope, String resourceName, String path, String version) {
        // Compose the authority as scope@resourceName and pass it to the authority-form URI
        // constructor, which stores it verbatim. The scheme and authority are the CRI's identity,
        // so they fold to lowercase; the path and version are payload details and keep their case.
        String authority = resourceName != null
                ? ((scope != null ? scope + "@" : "") + resourceName).toLowerCase(Locale.ROOT)
                : null;
        try {
            uri = new URI(scheme.toLowerCase(Locale.ROOT), authority, path, null, version);
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
        uri = URI.create(foldIdentity(rawCRI));
    }

    // Folds the scheme and authority of a raw CRI to lowercase, leaving the path and everything
    // after it untouched. The authority ends at the first '/', '?', or '#' after "://".
    private static String foldIdentity(String rawCRI) {
        String ret = rawCRI;
        int schemeEnd = rawCRI.indexOf("://");
        if (schemeEnd >= 0) {
            int authorityEnd = rawCRI.length();
            for (int i = schemeEnd + 3; i < rawCRI.length(); i++) {
                char c = rawCRI.charAt(i);
                if (c == '/' || c == '?' || c == '#') {
                    authorityEnd = i;
                    break;
                }
            }
            ret = rawCRI.substring(0, authorityEnd).toLowerCase(Locale.ROOT) + rawCRI.substring(authorityEnd);
        }
        return ret;
    }

    @Override
    public String scheme() {
        return uri.getScheme();
    }

    @Override
    public String scope() {
        // The raw authority is scope@[zone~]resourceName; scope is the part before '@', null when absent.
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
    public String zone() {
        String hostPart = hostPart();
        if (hostPart == null) {
            return null;
        }
        int delimiter = hostPart.indexOf(ZONE_DELIMITER);
        return delimiter >= 0 ? hostPart.substring(0, delimiter) : null;
    }

    @Override
    public boolean hasZone() {
        return zone() != null;
    }

    @Override
    public String resourceName() {
        String hostPart = hostPart();
        if (hostPart == null) {
            return null;
        }
        int delimiter = hostPart.indexOf(ZONE_DELIMITER);
        return delimiter >= 0 ? hostPart.substring(delimiter + 1) : hostPart;
    }

    // The authority after any scope@, holding [zone~]resourceName
    private String hostPart() {
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
        if(hasZone()){
            sb.append(zone());
            sb.append(ZONE_DELIMITER);
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
