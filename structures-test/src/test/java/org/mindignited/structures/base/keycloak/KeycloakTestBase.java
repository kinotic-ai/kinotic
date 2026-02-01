package org.mindignited.structures.base.keycloak;


import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(initializers = KeycloakTestContextInitializer.class)
public abstract class KeycloakTestBase {
    

}
