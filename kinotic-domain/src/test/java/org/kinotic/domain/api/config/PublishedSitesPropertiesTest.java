package org.kinotic.domain.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers {@link PublishedSitesProperties#resolveSiteUrl}: where a published site is served.
 */
class PublishedSitesPropertiesTest {

    @Test
    void aSiteIsServedOverHttpsOnTheDefaultPort() {
        PublishedSitesProperties sites = new PublishedSitesProperties().setDomain("kinotic.io");

        assertEquals("https://app--ui.org.kinotic.io", sites.resolveSiteUrl("app--ui.org"));
    }

    @Test
    void aSiteOfALocalEdgeIsServedOverHttpOnItsPort() {
        PublishedSitesProperties sites = new PublishedSitesProperties().setDomain("sites.localhost")
                                                                       .setUseSsl(false)
                                                                       .setPort(8088);

        assertEquals("http://app--ui.org.sites.localhost:8088", sites.resolveSiteUrl("app--ui.org"));
    }
}
