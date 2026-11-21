package org.kinotic.authz.api.model;

import java.util.Set;
import java.util.TreeSet;

/**
 * What a service's contract names of an authorization model. Two services naming a type in common decide that
 * type's permissions and roles together.
 *
 * @param types       the resource types the contract decides: the type the service declares, and each type a
 *                    function of it requires a permission of
 * @param parent      the type the service places its declared type inside, or null when it names none
 * @param permissions the model names of the permissions its functions require, each {@code <type>_<permission>}
 */
public record ServiceModelNames(Set<String> types, String parent, Set<String> permissions) {

    /**
     * Every type the contract names: the ones it decides, and the parent of the one it declares.
     */
    public Set<String> named() {
        Set<String> ret = new TreeSet<>(types);
        if (parent != null) {
            ret.add(parent);
        }
        return ret;
    }
}
