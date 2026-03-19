package org.kinotic.persistence.api.config;

/**
 * The type of security to use for the OpenAPI spec
 * Created by Navíd Mitchell 🤪 on 4/1/23.
 */
public enum OpenApiSecurityType {

    /**
     * No security
     */
    NONE,
    /**
     * Basic authentication
     */
    BASIC,
    /**
     * Bearer token authentication
     */
    BEARER

}
