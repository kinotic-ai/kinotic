package org.kinotic.core.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link CorsProperties#trustsOrigin}: the origins a browser flow may hand social sign-up tokens and GitHub App
 * install codes to.
 */
class CorsPropertiesTest {

    @Test
    void anOriginThePatternNamesIsTrusted() {
        CorsProperties cors = new CorsProperties().setAllowedOriginPattern("https:\\/\\/(portal\\.kinotic\\.ai|summit42\\.example)");

        assertTrue(cors.trustsOrigin("https://portal.kinotic.ai"));
        assertTrue(cors.trustsOrigin("https://summit42.example"));
    }

    @Test
    void anOriginThePatternOnlyPartlyMatchesIsNotTrusted() {
        CorsProperties cors = new CorsProperties().setAllowedOriginPattern("https:\\/\\/portal\\.kinotic\\.ai");

        assertFalse(cors.trustsOrigin("https://portal.kinotic.ai.attacker.example"));
        assertFalse(cors.trustsOrigin("http://portal.kinotic.ai"));
        assertFalse(cors.trustsOrigin(null));
    }

    @Test
    void aPatternOpenToEveryOriginTrustsNone() {
        CorsProperties cors = new CorsProperties().setAllowedOriginPattern("*");

        assertFalse(cors.trustsOrigin("https://portal.kinotic.ai"));
        assertFalse(cors.trustsOrigin("https://attacker.example"));
    }
}
