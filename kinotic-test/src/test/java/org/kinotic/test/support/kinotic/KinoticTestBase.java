package org.kinotic.test.support.kinotic;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

/**
 * Test base for tests that need the full Kinotic stack (Elasticsearch + kinotic-server)
 * via Docker Compose (compose.kinotic-test.yml).
 * Uses Testcontainers Docker Compose support.
 */
@ContextConfiguration(initializers = KinoticTestContextInitializer.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class KinoticTestBase {
}
