package org.kinotic.system.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers {@link UiDeploymentProperties#resolveSiteDirectoryUrl}: the directory URL a publish or removal workload is
 * handed.
 */
class UiDeploymentPropertiesTest {

    @Test
    void theSiteDirectoryIsUnderTheSitesContainer() {
        UiDeploymentProperties properties = new UiDeploymentProperties()
                .setSitesStorageEndpoint("https://stkinoticsites.blob.core.windows.net");

        assertEquals("https://stkinoticsites.blob.core.windows.net/sites/app--ui.org.kinotic.io",
                     properties.resolveSiteDirectoryUrl("app--ui.org.kinotic.io"));
    }

    @Test
    void anEndpointWithAnAccountPathAndATrailingSlashKeepsThePath() {
        UiDeploymentProperties properties = new UiDeploymentProperties()
                .setSitesStorageEndpoint("http://192.168.127.254:10000/devstoreaccount1/");

        assertEquals("http://192.168.127.254:10000/devstoreaccount1/sites/app--ui.org.sites.localhost",
                     properties.resolveSiteDirectoryUrl("app--ui.org.sites.localhost"));
    }
}
