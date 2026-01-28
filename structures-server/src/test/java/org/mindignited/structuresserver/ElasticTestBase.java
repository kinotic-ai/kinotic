package org.mindignited.structuresserver;


import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.mindignited.structures.api.annotations.EnableStructures;
import org.mindignited.structuresserver.config.ElasticsearchTestContextInitializer;

@ContextConfiguration(initializers = ElasticsearchTestContextInitializer.class)
@SpringBootTest
@EnableStructures
public abstract class ElasticTestBase {
    
}
