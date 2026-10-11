package org.kinotic.authz.api.model;

/**
 * Which kind of store a model is generated for, which decides the kernel types the model carries.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public enum AuthzStoreKind {

    /**
     * The one store holding the developer-side graph of every organization and the platform itself.
     */
    PLATFORM,

    /**
     * An application's own store, holding its end users, tenants and data grants.
     */
    APPLICATION
}
