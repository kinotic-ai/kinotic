package org.kinotic.authz.api.model;

/**
 * What an entity definition's rows are contained in, which is where a grant over all of its rows is bound.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public enum EntityScope {

    /**
     * Rows belong to the application as a whole, as for an entity definition with no multi-tenancy.
     */
    APPLICATION,

    /**
     * Rows belong to one tenant of the application, as for a shared multi-tenant entity definition.
     */
    TENANT
}
