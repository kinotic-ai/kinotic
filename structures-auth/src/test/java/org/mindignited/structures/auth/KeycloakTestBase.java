package org.mindignited.structures.auth;


import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.mindignited.structures.auth.config.KeycloakTestContextInitializer;

@SpringBootTest
@ContextConfiguration(initializers = KeycloakTestContextInitializer.class)
public abstract class KeycloakTestBase {
    

}
